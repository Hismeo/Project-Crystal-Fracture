package org.hismeo.nuquest.core.dialog.text;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.hismeo.nuquest.api.dialog.text.ITextEffect;
import org.hismeo.nuquest.core.dialog.SoundGroup;

public record DialogText(String title, ResourceLocation imagePath, String text, SoundGroup soundGroup, ITextEffect textEffect) {}
