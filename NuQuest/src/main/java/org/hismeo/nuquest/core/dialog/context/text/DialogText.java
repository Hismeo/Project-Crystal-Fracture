package org.hismeo.nuquest.core.dialog.context.text;

import org.hismeo.nuquest.api.dialog.ITextEffect;
import org.hismeo.nuquest.core.dialog.ImageGroup;
import org.hismeo.nuquest.core.dialog.SoundGroup;

public record DialogText(String title, ImageGroup imageGroup, String text, SoundGroup soundGroup, ITextEffect textEffect) {}
