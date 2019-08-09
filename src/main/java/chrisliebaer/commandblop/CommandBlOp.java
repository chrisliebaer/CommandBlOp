package chrisliebaer.commandblop;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.comphenix.protocol.wrappers.nbt.NbtWrapper;
import de.tr7zw.nbtapi.NBTContainer;
import de.tr7zw.nbtapi.NBTTileEntity;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

@Slf4j
public class CommandBlOp extends JavaPlugin implements Listener {
	
	private ProtocolManager protocolManager;
	private FakeOpInterceptor opInterceptor;
	
	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		
		// register op interceptor for fake op
		protocolManager = ProtocolLibrary.getProtocolManager();
		opInterceptor = new FakeOpInterceptor(protocolManager, this);
		protocolManager.addPacketListener(opInterceptor);
		
		// register on incoming command block updates
		protocolManager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.SET_COMMAND_BLOCK) {
			@Override
			public void onPacketReceiving(PacketEvent event) {
				onSetCommandPacket(event);
			}
		});
	}
	
	private boolean ignore(PacketEvent event) {
		var id = event.getPacketType().getCurrentId();
		return id == 16 || id == 74 || id == 33 || id == 14 || id == 18;
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent ev) {
		// ensure player has fake op on join
		opInterceptor.fakeOp(ev.getPlayer());
	}
	
	private void onSetCommandPacket(PacketEvent ev) {
		var player = ev.getPlayer();
		if (player.isOp() || !player.hasPermission(Permissions.EDIT))
			return;
		
		// cancel event since we are going to handle it ourself
		ev.setCancelled(true);
		
		var container = ev.getPacket();
		BlockPosition huh = container.getBlockPositionModifier().read(0);
		
		Location loc = container.getBlockPositionModifier().read(0).toLocation(player.getWorld());
		String command = container.getStrings().read(0);
		boolean trackOutput = container.getBooleans().read(0);
		boolean conditional = container.getBooleans().read(1);
		boolean automatic = container.getBooleans().read(2);
		
		// cause fuck you, that's why
		String mode = unfuckSetCommandPacket(container.getHandle());
		//log.info("updating cmdblock at {}, command: {}, mode: {}, track: {}, conditional: {}, automatic: {}",
				//loc, command, mode, trackOutput, conditional, automatic);
		
		// move update to main thread
		getServer().getScheduler().runTask(this, () -> {
			var block = loc.getBlock();
			
			if (!(block.getState() instanceof CommandBlock)) {
				log.warn("{} attempted to change non-existing command block at: {}", player, loc);
				return;
			}
			
			block.getState();
			block.getBlockData();
			
			// copy current nbt tag and restore after block update
			String nbtData = new NBTTileEntity(block.getState()).asNBTString();
			BlockFace facing = ((Directional) block.getBlockData()).getFacing();
			
			// update block according to new type
			block.setType(resolveCommandBlockType(mode));
			
			// copy over nbt data (the same accros all command block types)
			var nbt = new NBTTileEntity(block.getState());
			nbt.getKeys().forEach(nbt::removeKey);
			nbt.mergeCompound(new NBTContainer(nbtData));
			
			// update with new data
			nbt.setString("Command", command);
			nbt.setByte("TrackOutput", (byte) (trackOutput ? 1 : 0));
			nbt.setByte("auto", (byte) (automatic ? 1 : 0));
			
			// adjust facing (also props for making your own fucking class names collide *slow clap*)
			var blockData = block.getBlockData();
			Directional directionalData = (Directional) blockData;
			org.bukkit.block.data.type.CommandBlock commandBlockData = (org.bukkit.block.data.type.CommandBlock) blockData;
			
			commandBlockData.setConditional(conditional);
			directionalData.setFacing(facing);
			block.setBlockData(blockData);
		});
	}
	
	private Material resolveCommandBlockType(String mode) {
		switch (mode) {
			case "REDSTONE":
				return Material.COMMAND_BLOCK;
			case "SEQUENCE":
				return Material.CHAIN_COMMAND_BLOCK;
			case "AUTO":
				return Material.REPEATING_COMMAND_BLOCK;
			default:
				throw new RuntimeException("unknown command block type: " + mode);
		}
	}
	
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent ev) {
		var player = ev.getPlayer();
		var block = ev.getClickedBlock();
		if (block == null || player.isOp() || player.getGameMode() != GameMode.CREATIVE)
			return;
		
		var type = block.getType();
		if (type == Material.COMMAND_BLOCK ||
				type == Material.CHAIN_COMMAND_BLOCK ||
				type == Material.REPEATING_COMMAND_BLOCK) {
			
			if (ev.getAction() == Action.LEFT_CLICK_BLOCK && player.hasPermission(Permissions.BREAK)) {
				block.breakNaturally();
				return;
			}
			
			// sneaking allows to place blocks without activating command block, this is vanilla behavior
			if (!player.hasPermission(Permissions.VIEW) || player.isSneaking()) {
				if (handleCommandBlockPlace(ev)) {
					ev.setCancelled(true);
				}
				return;
			}
			
			// since canceling the event will also close the command block gui, we remove the item from the hand
			var inventory = player.getInventory();
			var stack = new ItemStack(inventory.getItemInMainHand());
			inventory.getItemInMainHand().setAmount(0);
			getServer().getScheduler().runTask(this, () -> player.getInventory().setItemInMainHand(stack));
			
			// send command block tile data to client for display
			CommandBlock cmdBlock = (CommandBlock) block.getState();
			sendCommandBlockTileData(player, cmdBlock);
		}
		
		if (handleCommandBlockPlace(ev)) {
			ev.setCancelled(true);
		}
	}
	
	private boolean handleCommandBlockPlace(PlayerInteractEvent ev) {
		var type = ev.getMaterial();
		var player = ev.getPlayer();
		var world = player.getWorld();
		if (type == Material.COMMAND_BLOCK ||
				type == Material.CHAIN_COMMAND_BLOCK ||
				type == Material.REPEATING_COMMAND_BLOCK) {
			if (!player.hasPermission(Permissions.PLACE) || ev.getAction() == Action.LEFT_CLICK_BLOCK)
				return false;
			
			// resolve clicked block to point of creation
			Block clicked = ev.getClickedBlock();
			if (clicked == null)
				return false;
			
			Location loc = clicked.getLocation().add(ev.getBlockFace().getDirection());
			Block block = loc.getBlock();
			ItemStack item = player.getInventory().getItemInMainHand();
			
			// place block via api
			block.setType(type);
			
			// find out which direction player is facing
			BlockFace face = getBlockFaceFromVector(player.getLocation().getDirection());
			
			Directional directional = (Directional) block.getBlockData();
			directional.setFacing(face.getOppositeFace()); // command blocks are inverted for some reason
			block.setBlockData(directional);
			
			return true;
		}
		
		return false;
	}
	
	private void sendCommandBlockTileData(Player player, CommandBlock commandBlock) {
		Location loc = commandBlock.getLocation();
		
		// https://wiki.vg/Protocol#Update_Block_Entity
		PacketContainer container = protocolManager.createPacket(PacketType.Play.Server.TILE_ENTITY_DATA);
		container.getBlockPositionModifier().write(0, new BlockPosition(loc.toVector()));
		container.getIntegers().write(0, 2); // this data contains command block text
		
		NBTTileEntity nbtTileEntity = new NBTTileEntity(commandBlock);
		NbtWrapper<Object> nbt = NbtFactory.fromNMS(nbtTileEntity.getCompound(), "root");
		container.getNbtModifier().write(0, nbt);
		
		try {
			protocolManager.sendServerPacket(player, container);
		} catch (InvocationTargetException e) {
			log.error("failed to send command block data to {}", player, e);
		}
	}
	
	private static BlockFace getBlockFaceFromVector(Vector vec) {
		double x = Math.abs(vec.getX());
		double y = Math.abs(vec.getY());
		double z = Math.abs(vec.getZ());
		
		if (x > z) {
			if (x > y) {
				// x largest
				return decideFacingFromValue(vec.getX(), BlockFace.EAST, BlockFace.WEST);
			} else {
				// y largest
				return decideFacingFromValue(vec.getY(), BlockFace.UP, BlockFace.DOWN);
			}
		} else {
			if (z > y) {
				// z largest
				return decideFacingFromValue(vec.getZ(), BlockFace.SOUTH, BlockFace.NORTH);
			} else {
				// y largest
				return decideFacingFromValue(vec.getY(), BlockFace.UP, BlockFace.DOWN);
			}
		}
	}
	
	private static BlockFace decideFacingFromValue(double val, BlockFace positive, BlockFace negative) {
		return val > 0 ? positive : negative;
	}
	
	@SneakyThrows
	private static String unfuckSetCommandPacket(Object handle) {
		Class<?> clazz = handle.getClass();
		
		for (Field field : clazz.getDeclaredFields()) {
			if (field.getType().getName().endsWith("TileEntityCommand$Type")) {
				field.setAccessible(true);
				Object o = field.get(handle); // this is an enum. i hope
				return o.toString();
			}
		}
		throw new RuntimeException("failed to located command block type, this likely means your version is incompatible");
	}
}
