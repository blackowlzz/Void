package ac.voidac.utils.anticheat;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalInt;

@UtilityClass
public class SemanticVersion {

    public @NotNull OptionalInt compare(@NotNull String first, @NotNull String second) {
        int[] left = parse(first);
        int[] right = parse(second);
        if (left == null || right == null) return OptionalInt.empty();
        for (int i = 0; i < left.length; i++) {
            if (left[i] != right[i]) return OptionalInt.of(Integer.compare(left[i], right[i]));
        }
        return OptionalInt.of(0);
    }

    private int[] parse(String version) {
        String core = version.trim().split("[-+]")[0];
        if (core.isEmpty()) return null;
        String[] parts = core.split("\\.");
        int[] numbers = new int[3];
        for (int i = 0; i < numbers.length && i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException exception) {
                return null;
            }
            if (numbers[i] < 0) return null;
        }
        return numbers;
    }
}
