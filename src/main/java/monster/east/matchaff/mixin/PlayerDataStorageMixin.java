package monster.east.matchaff.mixin;

import monster.east.matchaff.datafix.MatchaItemDataFixer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.IOException;
import java.nio.file.Path;

@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStorageMixin {
	@Redirect(
			method = "save",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V")
	)
	private static void matcha$markSavedPlayer(CompoundTag tag, Path path) throws IOException {
		MatchaItemDataFixer.markCurrent(tag);
		NbtIo.writeCompressed(tag, path);
	}
}
