package monster.east.matchaff.mixin;

import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Fabric API takes over the breaking animation for block models: it generates
 * a Mesh from the block model (including out-of-bounds leaf panels from the
 * datapack's cross_leaves and glow lichen ground models) and submits it
 * through the four-argument submitBreakingBlockModel on SubmitNodeCollection.
 * This mixin filters that Mesh, dropping any quad whose vertices fall outside
 * the 0..1 normalized block box, so the breaking cracks no longer show up in
 * the air around leaves. Normal rendering is untouched (it uses the chunk
 * mesh, not this path), so the out-of-bounds decoration stays visible.
 * Blocks without out-of-bounds quads are passed through unchanged.
 *
 * <p>The filtered mesh is rebuilt through {@link Renderer#get()} so it uses
 * whichever renderer owns the breaking mesh (Indigo or Sodium's FRAPI
 * implementation). Hardcoding Indigo's MutableMeshImpl here would crash with a
 * ClassCastException whenever Sodium owns the mesh.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class LeavesBreakingMixin {
	@ModifyVariable(
		method = "submitBreakingBlockModel(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/util/List;Lnet/fabricmc/fabric/api/client/renderer/v1/mesh/Mesh;I)V",
		at = @At("HEAD"),
		argsOnly = true
	)
	private Mesh matcha$dropOutOfBoundsBreakingQuads(Mesh mesh) {
		return filterOutOfBounds(mesh);
	}

	@Unique
	private static Mesh filterOutOfBounds(Mesh mesh) {
		if (mesh == null) {
			return null;
		}
		boolean[] hasOutOfBounds = {false};
		mesh.forEach(quad -> {
			if (quadOutOfBounds(quad)) {
				hasOutOfBounds[0] = true;
			}
		});
		if (!hasOutOfBounds[0]) {
			return mesh;
		}

		MutableMesh rebuilt = Renderer.get().mutableMesh();
		QuadEmitter emitter = rebuilt.emitter();
		mesh.forEach(quad -> {
			if (!quadOutOfBounds(quad)) {
				emitter.copyFrom(quad);
				emitter.emit();
			}
		});
		return rebuilt.immutableCopy();
	}

	@Unique
	private static boolean quadOutOfBounds(QuadView quad) {
		for (int i = 0; i < 4; i++) {
			// Fabric mesh quad positions are normalized to 0..1 (same space as BakedQuad).
			if (quad.x(i) < 0.0F || quad.x(i) > 1.0F
					|| quad.y(i) < 0.0F || quad.y(i) > 1.0F
					|| quad.z(i) < 0.0F || quad.z(i) > 1.0F) {
				return true;
			}
		}
		return false;
	}
}
