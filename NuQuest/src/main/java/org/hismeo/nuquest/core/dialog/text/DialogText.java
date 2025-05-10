package org.hismeo.nuquest.core.dialog.text;

import net.minecraft.sounds.SoundEvent;
import org.hismeo.nuquest.api.dialog.text.ITextEffect;

public record DialogText(String text, SoundEvent soundEvent, ITextEffect textEffect) {
}
