package org.hismeo.nuquest.core.dialog.context;

import org.hismeo.nuquest.core.dialog.context.action.IAction;

public record DialogActionData(String message, IAction action, Object... parameter) {
}
