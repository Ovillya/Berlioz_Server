package vai.berlioz.network;

/** Identifiers for messages exchanged on the Berlioz channel. */
public final class MessageType {
        public static final byte HELLO = 0x01;
        public static final byte WELCOME = 0x02;

        public static final byte PERMISSION = 0x06; // S->C [byte playlist][byte ambient]
        public static final byte CACHE_REQUEST = 0x07;
        public static final byte CACHE_ORDER = 0x08;
        public static final byte BROADCAST_START = 0x09; // [int radius][utf8 url]
        public static final byte PLAY_ORDER = 0x0A; // [float offset][byte paused][byte kind][url]; kind: 0=DJ 1=ambient 2=server
        public static final byte BROADCAST_PAUSE = 0x0B;
        public static final byte BROADCAST_RESUME = 0x0C;
        public static final byte BROADCAST_STOP = 0x0D;
        public static final byte BROADCAST_SEEK = 0x0E; // [float offsetSeconds]
        public static final byte STOP_ORDER = 0x0F;
        public static final byte PAUSE_ORDER = 0x10;
        public static final byte RESUME_ORDER = 0x11;
        public static final byte ZONE_LIST_REQUEST = 0x12; // C->S [int radius]
        public static final byte ZONE_LIST = 0x13; // S->C [int count]{zones}
        public static final byte ZONE_SAVE = 0x14; // C->S [byte hasId][16B id?][zone]
        public static final byte ZONE_DELETE = 0x15; // C->S [16B id]

        public static final byte ASK_NEXT = 0x17; // C->S empty (track finished)

        private MessageType() {}
}
