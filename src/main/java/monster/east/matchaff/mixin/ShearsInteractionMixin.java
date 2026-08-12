package monster.east.matchaff.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.monster.skeleton.Bogged;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.PumpkinBlock;
import net.minecraft.world.level.block.TripWireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({
		Entity.class,
		LeashFenceKnotEntity.class,
		Sheep.class,
		MushroomCow.class,
		SnowGolem.class,
		CopperGolem.class,
		Bogged.class,
		SulfurCube.class,
		BeehiveBlock.class,
		PumpkinBlock.class,
		TripWireBlock.class
})
public abstract class ShearsInteractionMixin {
	@Redirect(
			method = {"interact", "mobInteract", "useItemOn", "playerWillDestroy"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"),
			require = 0
	)
	private boolean matcha$acceptCustomShears(ItemStack stack, Object item) {
		return stack.is((Item) item)
				|| item == Items.SHEARS && stack.getItem() instanceof ShearsItem;
	}
}
