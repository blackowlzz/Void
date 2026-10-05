package ac.voidac.utils.anticheat;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.Modifying;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BooleanSupplier;

@UtilityClass
public class SmallCapsTag {
    public static final String NAME = "sc";

    public @NotNull TagResolver resolver(@NotNull BooleanSupplier enabled) {
        Modifying modifier = (component, depth) -> {
            Component bare = component.children(List.of());
            return bare instanceof TextComponent text && enabled.getAsBoolean()
                    ? text.content(SmallCaps.convert(text.content()))
                    : bare;
        };
        return TagResolver.resolver(NAME, (arguments, context) -> modifier);
    }
}
