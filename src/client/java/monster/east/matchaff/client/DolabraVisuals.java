package monster.east.matchaff.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static net.minecraft.core.registries.BuiltInRegistries.ITEM;

public final class DolabraVisuals {
	private static final float FLIPPED_ANGLE = 180.0F;
	private static final float ANGLE_STEP = 30.0F;
	private static boolean renderingPlayerInventorySlot;
	private static float previousMainHandAngle;
	private static float mainHandAngle;
	private static float previousOffHandAngle;
	private static float offHandAngle;
	private static float activeFirstPersonAngle;
	private static boolean guiMirrorRequested;
	private static boolean activeGuiMirror;
	private static boolean pickaxeMode;
	private static final Vector3f HANDLE_AXIS = new Vector3f(1.0F, 1.0F, 0.0F).normalize();

	private DolabraVisuals() {
	}

	public static void tick(Minecraft client) {
		previousMainHandAngle = mainHandAngle;
		previousOffHandAngle = offHandAngle;
		if (client.player == null || client.level == null) {
			mainHandAngle = 0.0F;
			offHandAngle = 0.0F;
			pickaxeMode = false;
			return;
		}

		if (isDolabra(client.player.getMainHandItem()) || isDolabra(client.player.getOffhandItem())) {
			updateMode(client);
		}
		mainHandAngle = Mth.approach(mainHandAngle, targetAngle(client.player.getMainHandItem()), ANGLE_STEP);
		offHandAngle = Mth.approach(offHandAngle, targetAngle(client.player.getOffhandItem()), ANGLE_STEP);
	}

	public static float firstPersonAngle(InteractionHand hand, ItemStack stack, float partialTick) {
		if (!MatchaClientConfig.dolabraVisuals() || !isDolabra(stack)) {
			return 0.0F;
		}
		float previous = hand == InteractionHand.MAIN_HAND ? previousMainHandAngle : previousOffHandAngle;
		float current = hand == InteractionHand.MAIN_HAND ? mainHandAngle : offHandAngle;
		return Mth.lerp(partialTick, previous, current);
	}

	public static void beginFirstPersonRender(InteractionHand hand, ItemStack stack, float partialTick) {
		activeFirstPersonAngle = firstPersonAngle(hand, stack, partialTick);
	}

	public static void endFirstPersonRender() {
		activeFirstPersonAngle = 0.0F;
	}

	public static void beginGuiItemRender(ItemStack stack) {
		guiMirrorRequested = shouldFlip(stack);
	}

	public static void endGuiItemRender() {
		guiMirrorRequested = false;
	}

	public static boolean guiMirrorRequested() {
		return guiMirrorRequested;
	}

	public static void beginRenderState(boolean guiMirror) {
		activeGuiMirror = guiMirror;
	}

	public static void endRenderState() {
		activeGuiMirror = false;
	}

	public static void applyItemRotation(PoseStack.Pose pose) {
		if (activeFirstPersonAngle != 0.0F) {
			pose.rotateAround(
					new Quaternionf().rotationAxis(activeFirstPersonAngle * Mth.DEG_TO_RAD, HANDLE_AXIS),
					0.55F,
					0.5F,
					0.5F
			);
		} else if (activeGuiMirror) {
			pose.rotate(new Quaternionf().rotationAxis(FLIPPED_ANGLE * Mth.DEG_TO_RAD, HANDLE_AXIS));
		}
	}

	public static void renderHotbarItem(
			GuiGraphicsExtractor graphics, LivingEntity owner, ItemStack stack, int x, int y, int seed) {
		beginGuiItemRender(stack);
		try {
			graphics.item(owner, stack, x, y, seed);
		} finally {
			endGuiItemRender();
		}
	}

	public static void setRenderingPlayerInventorySlot(boolean rendering) {
		renderingPlayerInventorySlot = rendering;
	}

	public static boolean renderingPlayerInventorySlot() {
		return renderingPlayerInventorySlot;
	}

	public static boolean isDolabra(ItemStack stack) {
		Identifier id = ITEM.getKey(stack.getItem());
		return id != null && id.getPath().endsWith("_dolabra");
	}

	public static boolean shouldFlip(ItemStack stack) {
		return MatchaClientConfig.dolabraVisuals() && isDolabra(stack) && pickaxeMode;
	}

	private static void updateMode(Minecraft client) {
		if (client.hitResult instanceof EntityHitResult entityHit) {
			if (entityHit.getEntity() instanceof LivingEntity
					&& client.player.isWithinAttackRange(
							client.player.getActiveItem(), entityHit.getEntity().getBoundingBox(), 0.0D)) {
				pickaxeMode = false;
			}
			return;
		}
		if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		var state = client.level.getBlockState(hit.getBlockPos());
		if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
			pickaxeMode = false;
		} else if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
			pickaxeMode = true;
		}
	}

	private static float targetAngle(ItemStack stack) {
		return shouldFlip(stack) ? FLIPPED_ANGLE : 0.0F;
	}

}
