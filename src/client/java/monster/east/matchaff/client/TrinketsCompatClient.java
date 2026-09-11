package monster.east.matchaff.client;

import com.mojang.blaze3d.vertex.PoseStack;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.client.TrinketRenderer;
import eu.pb4.trinkets.api.client.TrinketRendererRegistry;
import monster.east.matchaff.compat.TrinketsCompat;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Objects;

public final class TrinketsCompatClient {
	private TrinketsCompatClient() {
	}

	public static void init() {
		for (String id : TrinketsCompat.EARRING_IDS) {
			Item item = Objects.requireNonNull(BuiltInRegistries.ITEM.getValue(
					Identifier.fromNamespaceAndPath("matcha", id)));
			TrinketRendererRegistry.registerRenderer(item, EarringRenderer::new);
		}
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			TooltipDisplay display = stack.get(DataComponents.TOOLTIP_DISPLAY);
			if (!isMatchaEarring(stack) || display == null
					|| display.shows(DataComponents.ATTRIBUTE_MODIFIERS)) {
				return;
			}
			lines.removeIf(TrinketsCompatClient::isTrinketsAttributeLine);
		});
	}

	private static boolean isMatchaEarring(ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id.getNamespace().equals("matcha") && TrinketsCompat.EARRING_IDS.contains(id.getPath());
	}

	private static boolean isTrinketsAttributeLine(Component line) {
		if (!(line.getContents() instanceof TranslatableContents contents)) {
			return false;
		}
		String key = contents.getKey();
		return key.equals("trinkets.tooltip.attributes.all")
				|| key.equals("trinkets.tooltip.attributes.single")
				|| key.startsWith("attribute.modifier.");
	}

	private static final class EarringRenderer implements TrinketRenderer {
		private final EquipmentLayerRenderer equipmentRenderer;
		private final PlayerModel wideArmor;
		private final PlayerModel slimArmor;

		private EarringRenderer(EntityRendererProvider.Context context) {
			this.equipmentRenderer = context.getEquipmentRenderer();
			this.wideArmor = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_ARMOR.head()), false);
			this.slimArmor = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM_ARMOR.head()), true);
		}

		@Override
		public void submit(ItemStack stack, TrinketSlotAccess slotReference,
				EntityModel<? extends LivingEntityRenderState> contextModel,
				PoseStack poseStack, SubmitNodeCollector submit, int light,
				LivingEntityRenderState state, float limbAngle, float limbDistance) {
			if (!(state instanceof AvatarRenderState avatar)) {
				return;
			}
			Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
			if (equippable == null || equippable.assetId().isEmpty()) {
				return;
			}
			PlayerModel model = avatar.skin.model() == PlayerModelType.SLIM ? slimArmor : wideArmor;
			equipmentRenderer.renderLayers(
				EquipmentClientInfo.LayerType.HUMANOID,
				equippable.assetId().orElseThrow(), model, avatar, stack,
				poseStack, submit, light, null, avatar.outlineColor, 0);
		}
	}
}
