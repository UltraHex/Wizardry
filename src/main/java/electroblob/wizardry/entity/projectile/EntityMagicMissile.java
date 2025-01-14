package electroblob.wizardry.entity.projectile;

import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.registry.WizardrySounds;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.ParticleBuilder;
import electroblob.wizardry.util.ParticleBuilder.Type;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class EntityMagicMissile extends EntityMagicArrow {

	/** Creates a new magic missile in the given world. */
	public EntityMagicMissile(World world){
		super(world);
	}

	@Override public double getDamage(){ return Spells.magic_missile.getProperty(Spell.DAMAGE).floatValue(); }

	@Override public int getLifetime(){ return 12; }

	@Override public boolean doGravity(){ return false; }

	@Override public boolean doDeceleration(){ return false; }

	@Override
	public void onEntityHit(EntityLivingBase entityHit){
		this.playSound(WizardrySounds.ENTITY_MAGIC_MISSILE_HIT, 1.0F, 1.2F / (this.rand.nextFloat() * 0.2F + 0.9F));
		if(this.world.isRemote) ParticleBuilder.create(Type.FLASH).pos(posX, posY, posZ).clr(1, 1, 0.65f).spawn(world);
	}
	
	@Override
	public void onBlockHit(RayTraceResult hit){
		if(this.world.isRemote){
			// Gets a position slightly away from the block hit so the particle doesn't get cut in half by the block face
			Vec3d vec = hit.hitVec.add(new Vec3d(hit.sideHit.getDirectionVec()).scale(0.15));
			ParticleBuilder.create(Type.FLASH).pos(vec).clr(1, 1, 0.65f).fade(0.85f, 0.5f, 0.8f).spawn(world);
		}
	}

	@Override
	public void tickInAir(){

		if(this.world.isRemote){
			ParticleBuilder.create(Type.SPARKLE, rand, posX, posY, posZ, 0.03, true).clr(1, 1, 0.65f).fade(0.7f, 0, 1)
			.time(20 + rand.nextInt(10)).spawn(world);
			
			if(this.ticksExisted > 1){ // Don't spawn particles behind where it started!
				double x = posX - motionX/2;
				double y = posY - motionY/2;
				double z = posZ - motionZ/2;
				ParticleBuilder.create(Type.SPARKLE, rand, x, y, z, 0.03, true).clr(1, 1, 0.65f).fade(0.7f, 0, 1)
				.time(20 + rand.nextInt(10)).spawn(world);
			}
		}
	}

	@Override
	protected void entityInit(){ }

}