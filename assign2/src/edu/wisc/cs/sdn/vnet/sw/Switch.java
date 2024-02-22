package edu.wisc.cs.sdn.vnet.sw;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

public class Switch extends Device
{
    private Map<Long, MacEntry> macTable;
    private Timer timer;

    public Switch(String host, DumpFile logfile)
    {
        super(host, logfile);
        this.macTable = new HashMap<>();
        this.timer = new Timer();

		// TODO should this go here?
		startTimeoutChecker();
    }

    public void handlePacket(Ethernet etherPacket, Iface inIface)
    {
        System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));

        // Update MAC table
        long macAddress = Ethernet.toLong(etherPacket.getSourceMACAddress());
        if (!macTable.containsKey(macAddress)) {
            macTable.put(macAddress, new MacEntry(inIface));
        } else {
            MacEntry entry = macTable.get(macAddress);
            entry.setLastSeen(System.currentTimeMillis());
        }

        System.out.println("interfaces:" + interfaces);

        System.out.println("\n\nSWITCH DONE.\n\n");
    }

    private class MacEntry {
        private Iface iface;
        private long lastSeen;

        public MacEntry(Iface iface) {
            this.iface = iface;
            this.lastSeen = System.currentTimeMillis();
        }

        public Iface getIface() {
            return iface;
        }

        public void setIface(Iface iface) {
            this.iface = iface;
        }

        public long getLastSeen() {
            return lastSeen;
        }

        public void setLastSeen(long lastSeen) {
            this.lastSeen = lastSeen;
        }
    }

    // Periodically check and remove entries older than 15 seconds
    private void startTimeoutChecker() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                macTable.entrySet().removeIf(entry ->
                        (currentTime - entry.getValue().getLastSeen()) > 15000);
            }
        }, 1000, 1000); // Check every second
    }
}
