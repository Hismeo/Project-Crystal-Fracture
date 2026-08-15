package org.hismeo.crystalfracture.weapon.internal.loader;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponDefinitionLoadException;
import org.hismeo.crystalfracture.weapon.internal.validation.WeaponDefinitionProblem;

import java.io.Reader;
import java.util.function.Function;

final class WeaponLoaderSupport {
    private WeaponLoaderSupport() {
    }

    static <T> LoadedWeaponDefinition<T> load(
            ResourceLocation resourceId,
            Reader reader,
            Function<JsonElement, T> parser
    ) {
        try {
            return LoadedWeaponDefinition.success(parser.apply(WeaponStrictJsonParser.parse(reader)));
        } catch (WeaponJsonException exception) {
            throw failure(resourceId, exception.path(), exception.errorCode(), exception.getMessage());
        } catch (IllegalStateException exception) {
            throw failure(resourceId, "$", "invalid_json", safeMessage(exception));
        }
    }

    static <T> LoadedWeaponDefinition<T> load(
            ResourceLocation resourceId,
            String json,
            Function<JsonElement, T> parser
    ) {
        try {
            return LoadedWeaponDefinition.success(parser.apply(WeaponStrictJsonParser.parse(json)));
        } catch (WeaponJsonException exception) {
            throw failure(resourceId, exception.path(), exception.errorCode(), exception.getMessage());
        } catch (IllegalStateException exception) {
            throw failure(resourceId, "$", "invalid_json", safeMessage(exception));
        }
    }

    static WeaponDefinitionLoadException failure(
            ResourceLocation resource,
            String path,
            String code,
            String message
    ) {
        return new WeaponDefinitionLoadException(
                java.util.List.of(WeaponDefinitionProblem.error(code, resource, path, message)));
    }

    private static String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
