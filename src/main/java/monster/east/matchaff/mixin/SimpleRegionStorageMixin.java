package monster.east.matchaff.mixin;

import monster.east.matchaff.datafix.MatchaItemDataFixer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Migrates block and entity region data before callers can skip vanilla datafixing. */
@Mixin(SimpleRegionStorage.class)
public abstract class SimpleRegionStorageMixin {
	@Shadow @Final private DataFixTypes dataFixType;

	@Inject(
			method = "read",
			at = @At("RETURN"),
			cancellable = true
	)
	private void matcha$migrateItems(ChunkPos pos,
			CallbackInfoReturnable<CompletableFuture<Optional<CompoundTag>>> cir) {
		if (this.dataFixType == DataFixTypes.CHUNK || this.dataFixType == DataFixTypes.ENTITY_CHUNK) {
			// C2ME skips chunk upgrades when vanilla DataVersion is current, regardless of Matcha's version.
			cir.setReturnValue(cir.getReturnValue().thenApplyAsync(
					data -> data.map(MatchaItemDataFixer::updateIfNeeded), Util.backgroundExecutor()));
		}
	}

	@ModifyArg(
			method = "write(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/storage/IOWorker;store(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"),
			index = 1
	)
	private Supplier<CompoundTag> matcha$markSavedChunk(Supplier<CompoundTag> supplier) {
		if (this.dataFixType != DataFixTypes.CHUNK && this.dataFixType != DataFixTypes.ENTITY_CHUNK) {
			return supplier;
		}
		return () -> {
			CompoundTag tag = supplier.get();
			MatchaItemDataFixer.markCurrent(tag);
			return tag;
		};
	}
}
