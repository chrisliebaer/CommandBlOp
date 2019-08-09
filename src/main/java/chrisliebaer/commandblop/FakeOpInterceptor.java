package chrisliebaer.commandblop;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;

@Slf4j
public class FakeOpInterceptor extends PacketAdapter {
	
	private static final byte OP_PERMISSION_LEVEL_4 = 28;
	
	private ProtocolManager protocolManager;
	
	public FakeOpInterceptor(ProtocolManager protocolManager, Plugin plugin) {
		super(plugin, PacketType.Play.Server.ENTITY_STATUS);
		this.protocolManager = protocolManager;
	}
	
	@Override
	public void onPacketSending(PacketEvent event) {
		var player = event.getPlayer();
		if (event.getPacketType() != PacketType.Play.Server.ENTITY_STATUS)
			return;
		
		PacketContainer container = event.getPacket();
		
		// check if op level change
		byte status = container.getBytes().read(0);
		if (24 > status || status > 28) // https://wiki.vg/Entity_statuses#Player
			return;
		
		if (!player.hasPermission(Permissions.FAKE_OP) || player.isOp())
			return;
		
		container.getBytes().write(0, OP_PERMISSION_LEVEL_4);
	}
	
	public void fakeOp(Player player) {
		if (!player.hasPermission(Permissions.FAKE_OP) || player.isOp())
			return;
		
		PacketContainer container = new PacketContainer(PacketType.Play.Server.ENTITY_STATUS);
		container.getIntegers().write(0, player.getEntityId());
		container.getBytes().write(0, (byte) 28);
		
		try {
			protocolManager.sendServerPacket(player, container);
		} catch (InvocationTargetException e) {
			log.error("failed to send fake op to {}", player, e);
		}
	}
}
