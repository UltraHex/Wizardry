package electroblob.wizardry.potion;

import electroblob.wizardry.Wizardry;
import electroblob.wizardry.item.ItemArtefact;
import electroblob.wizardry.packet.PacketEndSlowTime;
import electroblob.wizardry.packet.WizardryPacketHandler;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.registry.WizardryPotions;
import electroblob.wizardry.spell.SlowTime;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.ParticleBuilder;
import electroblob.wizardry.util.WizardryUtilities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

@Mod.EventBusSubscriber
public class PotionSlowTime extends PotionMagicEffect implements ISyncedPotion {

	// FIXME: Minecarts with entities in them (and, I suspect, any other ridden entities) go crazy when time-slowed

	public PotionSlowTime(boolean isBadEffect, int liquidColour){
		super(isBadEffect, liquidColour, new ResourceLocation(Wizardry.MODID, "textures/gui/potion_icon_slow_time.png"));
		this.setPotionName("potion." + Wizardry.MODID + ":slow_time");
	}

	private static double getEffectRadius(){
		return Spells.slow_time.getProperty(Spell.EFFECT_RADIUS).doubleValue();
	}

	public static void unblockNearbyEntities(EntityLivingBase host){
		List<Entity> targetsBeyondRange = WizardryUtilities.getEntitiesWithinRadius(getEffectRadius() + 3, host.posX, host.posY, host.posZ, host.world, Entity.class);
		targetsBeyondRange.forEach(e -> e.updateBlocked = false);
	}

	// Not done in performEffect because it's client-inconsistent; it only fires on the client of the player with the
	// potion effect, and doesn't fire on the client at all for non-players
	private static void performEffectConsistent(EntityLivingBase host, int strength){

		boolean stopTime = host instanceof EntityPlayer && ItemArtefact.isArtefactActive((EntityPlayer)host, WizardryItems.charm_stop_time);

		int interval = strength * 4 + 6;

		// Mark all entities within range
		List<Entity> targetsInRange = WizardryUtilities.getEntitiesWithinRadius(getEffectRadius(), host.posX, host.posY, host.posZ, host.world, Entity.class);
		targetsInRange.remove(host);
		// Other entities with the slow time effect are unaffected
		targetsInRange.removeIf(t -> t instanceof EntityLivingBase && ((EntityLivingBase)t).isPotionActive(WizardryPotions.slow_time));
		if(!Wizardry.settings.slowTimeAffectsPlayers) targetsInRange.removeIf(t -> t instanceof EntityPlayer);
		targetsInRange.removeIf(t -> t instanceof EntityArrow && t.isEntityInsideOpaqueBlock());

		for(Entity entity : targetsInRange){

			// If time is stopped, block all updates; otherwise block all updates except every [interval] ticks
			entity.updateBlocked = stopTime || host.ticksExisted % interval != 0;

			if(!stopTime && entity.world.isRemote){

				// Client-side movement interpolation (smoothing)

				if(entity.onGround) entity.motionY = 0; // Don't ask. It just works.

//				if(entity instanceof EntityLivingBase){
//					((EntityLivingBase)entity).prevLimbSwingAmount = ((EntityLivingBase)entity).limbSwingAmount;
//					((EntityLivingBase)entity).swingProgress = ((EntityLivingBase)entity).prevSwingProgress;
//					((EntityLivingBase)entity).renderYawOffset = ((EntityLivingBase)entity).prevRenderYawOffset;
//					((EntityLivingBase)entity).rotationYawHead = ((EntityLivingBase)entity).prevRotationYawHead;
//				}

				if(entity.updateBlocked){
					// When the update is blocked, the entity is moved 1/interval times the distance it would have moved
					double x = entity.posX + entity.motionX * 1d / (double)interval;
					double y = entity.posY + entity.motionY * 1d / (double)interval;
					double z = entity.posZ + entity.motionZ * 1d / (double)interval;

					entity.prevPosX = entity.posX;
					entity.prevPosY = entity.posY;
					entity.prevPosZ = entity.posZ;

					entity.posX = x;
					entity.posY = y;
					entity.posZ = z;

				}else{
					// When the update is not blocked, the entity is moved BACK 1-1/interval times the distance it moved
					// This is because the entity already covered most of that distance when its update was blocked
					entity.posX += entity.motionX * 1d / (double)interval;
					entity.posY += entity.motionY * 1d / (double)interval;
					entity.posZ += entity.motionZ * 1d / (double)interval;

					double x = entity.posX - entity.motionX * 1d / (double)interval;
					double y = entity.posY - entity.motionY * 1d / (double)interval;
					double z = entity.posZ - entity.motionZ * 1d / (double)interval;

					entity.prevPosX = x;
					entity.prevPosY = y;
					entity.prevPosZ = z;
				}
			}

			if(entity.world.isRemote && host.ticksExisted % 2 == 0){
				int lifetime = 15;
				double dx = (entity.world.rand.nextDouble() - 0.5D) * 2 * (double)entity.width;
				double dy = (entity.world.rand.nextDouble() - 0.5D) * 2 * (double)entity.width;
				double dz = (entity.world.rand.nextDouble() - 0.5D) * 2 * (double)entity.width;
				double x = entity.posX + dx;
				double y = entity instanceof IProjectile ? entity.posY + dy : entity.posY + entity.height/2 + dy;
				double z = entity.posZ + dz;
				ParticleBuilder.create(ParticleBuilder.Type.DUST)
						.pos(x, y, z)
						.vel(-dx/lifetime, -dy/lifetime, -dz/lifetime)
						.clr(0x5be3bb).time(15).spawn(entity.world);
			}
		}

		// Un-mark all entities that have just left range
		List<Entity> targetsBeyondRange = WizardryUtilities.getEntitiesWithinRadius(getEffectRadius() + 3, host.posX, host.posY, host.posZ, host.world, Entity.class);
		targetsBeyondRange.removeAll(targetsInRange);
		targetsBeyondRange.forEach(e -> e.updateBlocked = false);

	}

	@SubscribeEvent
	public static void onLivingUpdateEvent(LivingUpdateEvent event){

		EntityLivingBase entity = event.getEntityLiving();

		if(entity.isPotionActive(WizardryPotions.slow_time)){
			performEffectConsistent(entity, entity.getActivePotionEffect(WizardryPotions.slow_time).getAmplifier());
		}
	}

	@SubscribeEvent
	public static void onPotionAddedEvent(PotionEvent.PotionAddedEvent event){
		if(event.getEntity().world.isRemote && event.getPotionEffect().getPotion() == WizardryPotions.slow_time
				&& event.getEntity() == net.minecraft.client.Minecraft.getMinecraft().player){
			if(Wizardry.settings.useShaders) net.minecraft.client.Minecraft.getMinecraft().entityRenderer.loadShader(SlowTime.SHADER);
			electroblob.wizardry.client.WizardryClientEventHandler.playBlinkEffect();
		}
	}

	@SubscribeEvent
	public static void onPotionExpiryEvent(PotionEvent.PotionExpiryEvent event){
		if(event.getPotionEffect() != null && event.getPotionEffect().getPotion() == WizardryPotions.slow_time){
			unblockNearbyEntities(event.getEntityLiving());
			if(!event.getEntity().world.isRemote){
				WizardryPacketHandler.net.sendToDimension(new PacketEndSlowTime.Message(event.getEntityLiving()), event.getEntity().dimension);
			}
		}
	}

	@SubscribeEvent
	public static void onPotionRemoveEvent(PotionEvent.PotionRemoveEvent event){
		if(event.getPotionEffect() != null && event.getPotionEffect().getPotion() == WizardryPotions.slow_time){
			unblockNearbyEntities(event.getEntityLiving());
			if(!event.getEntity().world.isRemote){
				WizardryPacketHandler.net.sendToDimension(new PacketEndSlowTime.Message(event.getEntityLiving()), event.getEntity().dimension);
			}
		}
	}

}
