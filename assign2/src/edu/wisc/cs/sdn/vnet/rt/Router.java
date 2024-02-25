package edu.wisc.cs.sdn.vnet.rt;

import java.nio.ByteBuffer;
import java.util.Map;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Router Received packet: " +
				etherPacket.toString().replace("\n", "\n\t"));
		
		// Drop packet if not IPv4
		if (etherPacket.getEtherType() != 0x0800) return;

		// Get IP header from packet
		IPv4 ipv4Packet = (IPv4) etherPacket.getPayload();
		int hLen = ipv4Packet.getHeaderLength() * 4;
		short prevCheck = ipv4Packet.getChecksum();
		ipv4Packet.setChecksum((short)0);

		byte[] data = new byte[hLen];
		ByteBuffer bb = ByteBuffer.wrap(data);

		// Borrowed from IPv4 serialize()
		bb.rewind();
		int accumulation = 0;
		for (int i = 0; i < hLen * 2; ++i) {
			accumulation += 0xffff & bb.getShort();
		}
		accumulation = ((accumulation >> 16) & 0xffff)
				+ (accumulation & 0xffff);
		short newCheck = (short) (~accumulation & 0xffff);

		// Drop packet if checksums don't match
		if (prevCheck != newCheck) return;

		// TODO: Does this work correctly? TTL is type byte
		ipv4Packet.setTtl((byte)(ipv4Packet.getTtl() - 1));

		// Drop packet if TTL expires
		if (ipv4Packet.getTtl() == 0) return;

		// Destination IP address
		int destIP = ipv4Packet.getDestinationAddress();

		for (Map.Entry<String, Iface> iface : this.interfaces.entrySet()){
			// Drop packet if it matches a router interface IP
			if (iface.getValue().getIpAddress() == destIP) return;
		}

		// HANDLE FORWARDING

		// Lookup the RouteEntry
		RouteEntry routeEntry = this.routeTable.lookup(destIP);
            
		// Drop the packet if no matching entry found
		if (routeEntry == null) {
			return;
		}

		// Lookup the next-hop IP address
		int nextHopIp = routeEntry.getGatewayAddress();

		// Lookup MAC address corresponding to next-hop IP address
		MACAddress nextHopMac = this.arpCache.lookup(nextHopIp).getMac();
		if (nextHopMac == null) {
			return; // Drop the packet if MAC address not found
		}

		// Update Ethernet header
		etherPacket.setDestinationMACAddress(nextHopMac.toBytes());
		etherPacket.setSourceMACAddress(inIface.getMacAddress().toBytes());

		// Send the packet out the correct interface
		sendPacket(etherPacket, routeEntry.getInterface());
		
		System.out.println("*** -> Router Sent packet: " +
				etherPacket.toString().replace("\n", "\n\t"));
	}
}
