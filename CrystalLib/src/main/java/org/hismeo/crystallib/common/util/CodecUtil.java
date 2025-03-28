package org.hismeo.crystallib.common.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Tuple;

public class CodecUtil {
    public static <A, B> Codec<Tuple<A, B>> tupleCodec(Codec<A> aCodec, Codec<B> bCodec) {
        return RecordCodecBuilder.create(instance -> instance.group(
                aCodec.fieldOf("a").forGetter(Tuple::getA),
                bCodec.fieldOf("b").forGetter(Tuple::getB)
        ).apply(instance, Tuple::new));
    }
}
