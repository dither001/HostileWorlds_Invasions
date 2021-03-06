package com.corosus.inv;

import CoroUtil.block.TileEntityRepairingBlock;
import CoroUtil.config.ConfigDynamicDifficulty;
import CoroUtil.difficulty.UtilEntityBuffs;
import CoroUtil.util.*;
import com.corosus.inv.capabilities.PlayerDataInstance;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent;

import com.corosus.inv.config.ConfigAdvancedOptions;
import com.corosus.inv.config.ConfigInvasion;

import java.util.Iterator;
import java.util.List;

public class EventHandlerForge {


    public EventHandlerForge() {

	}

	@SubscribeEvent
	public void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
		if (event.getObject() instanceof EntityPlayer) {
			event.addCapability(new ResourceLocation(Invasion.modID, "PlayerDataInstance"), new ICapabilitySerializable<NBTTagCompound>() {
				PlayerDataInstance instance = Invasion.PLAYER_DATA_INSTANCE.getDefaultInstance().setPlayer((EntityPlayer)event.getObject());

				@Override
				public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
					return capability == Invasion.PLAYER_DATA_INSTANCE;
				}

				@Override
				public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
					return capability == Invasion.PLAYER_DATA_INSTANCE ? Invasion.PLAYER_DATA_INSTANCE.<T>cast(this.instance) : null;
				}

				@Override
				public NBTTagCompound serializeNBT() {
					return (NBTTagCompound) Invasion.PLAYER_DATA_INSTANCE.getStorage().writeNBT(Invasion.PLAYER_DATA_INSTANCE, this.instance, null);
				}

				@Override
				public void deserializeNBT(NBTTagCompound nbt) {
					Invasion.PLAYER_DATA_INSTANCE.getStorage().readNBT(Invasion.PLAYER_DATA_INSTANCE, this.instance, null, nbt);
				}
			});
		}
	}

	@SubscribeEvent
	public void canSleep(PlayerSleepInBedEvent event) {
		EntityPlayer player = event.getEntityPlayer();
		if (event.getEntityPlayer().world.isRemote) return;

		//this doesnt work for mods like morpheus, added an extra denial in player tick to force wake them
		if (ConfigInvasion.preventSleepDuringInvasions) {
			if (InvasionManager.shouldLockOutFeaturesForPossibleActiveInvasion(player.world)) {
				player.sendMessage(new TextComponentString(ConfigInvasion.Invasion_Message_cantSleep));
				event.setResult(EntityPlayer.SleepResult.NOT_SAFE);
			}
		}
	}

	@SubscribeEvent
	public void canTeleportDimensions(EntityTravelToDimensionEvent event) {

		if (ConfigInvasion.preventDimensionTeleportingDuringInvasions) {
			if (event.getEntity() instanceof EntityPlayer) {
				if (CoroUtilWorldTime.isNightPadded(event.getEntity().world) && InvasionManager.isInvasionTonight(event.getEntity().world)) {
					//if teleporting AWAY from overworld, stop them, but we want to allow them to come to it for invasion
					//also allow skipping players to teleport, should be fine?
					if (!InvasionManager.isPlayerSkippingInvasion((EntityPlayer)event.getEntity()) && event.getDimension() != 0) {
						event.getEntity().sendMessage(new TextComponentString(ConfigInvasion.Invasion_Message_cantTeleport));
						event.setCanceled(true);
					}
				}
			}
		}

	}

	@SubscribeEvent
	public void canDespawn(LivingSpawnEvent.AllowDespawn event) {

		//prevent invasion spawned entities from despawning during invasion if they are too far away

		if (CoroUtilWorldTime.isNightPadded(event.getEntity().world) && InvasionManager.isInvasionTonight(event.getEntity().world)) {
			if (event.getEntity().getEntityData().getBoolean(UtilEntityBuffs.dataEntityWaveSpawned)) {
				event.setResult(Event.Result.DENY);
			}
		}
	}
	
	@SubscribeEvent
	public void tickServer(ServerTickEvent event) {
		
		if (event.phase == Phase.START) {
			World world = DimensionManager.getWorld(0);
			if (world != null) {
				if (world.getTotalWorldTime() % 20 == 0) {
					for (EntityPlayer player : world.playerEntities) {
						if (CoroUtilEntity.canProcessForList(CoroUtilEntity.getName(player), ConfigAdvancedOptions.blackListPlayers, ConfigAdvancedOptions.useBlacklistAsWhitelist)) {
							InvasionManager.tickPlayerSlowPre(player);
						}
					}
					for (EntityPlayer player : world.playerEntities) {
						if (CoroUtilEntity.canProcessForList(CoroUtilEntity.getName(player), ConfigAdvancedOptions.blackListPlayers, ConfigAdvancedOptions.useBlacklistAsWhitelist)) {
							InvasionManager.tickPlayerSlow(player);
						}
					}
				}
				for (EntityPlayer player : world.playerEntities) {
					if (CoroUtilEntity.canProcessForList(CoroUtilEntity.getName(player), ConfigAdvancedOptions.blackListPlayers, ConfigAdvancedOptions.useBlacklistAsWhitelist)) {
						InvasionManager.tickPlayer(player);
					}
				}
			}
		}
	}

	@SubscribeEvent
	public void playerCloneEvent(PlayerEvent.LivingUpdateEvent event) {

	}

	@SubscribeEvent
	public void playerCloneEvent(PlayerEvent.Clone event) {

		/*System.out.println("old: " + event.getOriginal().getCapability(Invasion.PLAYER_DATA_INSTANCE, null).getDifficultyForInvasion());
		System.out.println("new: " + event.getEntityPlayer().getCapability(Invasion.PLAYER_DATA_INSTANCE, null).getDifficultyForInvasion());*/

		PlayerDataInstance oldInstance = event.getOriginal().getCapability(Invasion.PLAYER_DATA_INSTANCE, null);
        PlayerDataInstance newInstance = event.getEntityPlayer().getCapability(Invasion.PLAYER_DATA_INSTANCE, null);

		//migrate cap data to new player
		NBTTagCompound nbtOld = new NBTTagCompound();
        oldInstance.writeNBT(nbtOld);
        newInstance.readNBT(nbtOld);

        newInstance.copyRuntimeData(oldInstance);

		/*System.out.println("old: " + event.getOriginal().getCapability(Invasion.PLAYER_DATA_INSTANCE, null).getDifficultyForInvasion());
		System.out.println("new: " + event.getEntityPlayer().getCapability(Invasion.PLAYER_DATA_INSTANCE, null).getDifficultyForInvasion());*/
	}

	//use lowest to make sure FTBU claimed chunks take priority and do their work first
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void explosionEvent(ExplosionEvent.Detonate event) {

		if (event.getWorld().isRemote) return;

		if (ConfigInvasion.convertExplodedBlocksToRepairingBlocksDuringInvasions || ConfigInvasion.preventExplodedTileEntitiesDuringInvasions) {
			if (InvasionManager.shouldLockOutFeaturesForPossibleActiveInvasion(event.getWorld())) {
				List<BlockPos> listPos = event.getExplosion().getAffectedBlockPositions();

				for (Iterator<BlockPos> it = listPos.iterator(); it.hasNext(); ) {
					BlockPos pos = it.next();
					if (ConfigInvasion.preventExplodedTileEntitiesDuringInvasions && event.getWorld().getTileEntity(pos) != null) {
						it.remove();
					} else if (ConfigInvasion.convertExplodedBlocksToRepairingBlocksDuringInvasions) {
						IBlockState state = event.getWorld().getBlockState(pos);
						if (UtilMining.canMineBlock(event.getWorld(), pos, state.getBlock()) &&
								UtilMining.canConvertToRepairingBlock(event.getWorld(), state)) {
							TileEntityRepairingBlock.replaceBlockAndBackup(event.getWorld(), pos);
							it.remove();
						}
					}
				}
			}
		}
	}

	@SubscribeEvent
	public void entityTick(LivingEvent.LivingUpdateEvent event) {

		EntityLivingBase ent = event.getEntityLiving();
		if (!ent.world.isRemote) {
			if (ConfigInvasion.damagePerSecondToInvadersAtSunrise > 0 && ent.world.isDaytime()) {
				if (ent.getEntityData().getBoolean(UtilEntityBuffs.dataEntityWaveSpawned)) {
					if ((ent.world.getTotalWorldTime() + ent.getEntityId()) % 20 == 0) {
						ent.attackEntityFrom(DamageSource.OUT_OF_WORLD, (float) ConfigInvasion.damagePerSecondToInvadersAtSunrise);
						ent.setFire(1);
					}
				}
			}

			if (ent instanceof EntityPlayer) {
				EntityPlayer player = (EntityPlayer) ent;
				if (CoroUtilEntity.canProcessForList(CoroUtilEntity.getName(player), ConfigAdvancedOptions.blackListPlayers, ConfigAdvancedOptions.useBlacklistAsWhitelist)) {
					InvasionManager.tickPlayerEverywhere(player);
				}
			}
		}
	}
}
