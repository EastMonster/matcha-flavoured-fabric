package monster.east.matchaff.mixin;

import monster.east.matchaff.datafix.MatchaItemDataFixer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

/** Migrates loaded block and entity region data whose vanilla DataVersion is current. */
@Mixin(SimpleRegionStorage.class)
public abstract class SimpleRegionStorageMixin {
	@Shadow @Final private DataFixTypes dataFixType;

	@Inject(
			method = "upgradeChunkTag(Lnet/minecraft/nbt/CompoundTag;ILnet/minecraft/nbt/CompoundTag;I)Lnet/minecraft/nbt/CompoundTag;",
			at = @At("RETURN"),
			cancellable = true
	)
	private void matcha$migrateItems(CompoundTag chunkTag, int defaultVersion, CompoundTag contextTag, int targetVersion,
			CallbackInfoReturnable<CompoundTag> cir) {
		if (this.dataFixType == DataFixTypes.CHUNK || this.dataFixType == DataFixTypes.ENTITY_CHUNK) {
			cir.setReturnValue(MatchaItemDataFixer.updateIfNeeded(cir.getReturnValue()));
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
