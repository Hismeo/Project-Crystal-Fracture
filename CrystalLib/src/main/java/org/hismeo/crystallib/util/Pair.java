package org.hismeo.crystallib.util;

public record Pair<F, S>(F first, S second) {
    public Pair<S, F> swap() {
        return new Pair<>(this.second, this.first);
    }
}
