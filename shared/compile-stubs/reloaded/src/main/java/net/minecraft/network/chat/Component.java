package net.minecraft.network.chat;


public interface Component {
    default String getString() { throw new UnsupportedOperationException(); }
    default MutableComponent copy() { throw new UnsupportedOperationException(); }
    static MutableComponent literal(String text) { throw new UnsupportedOperationException(); }
}
