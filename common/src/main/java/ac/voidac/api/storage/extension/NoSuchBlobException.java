package ac.voidac.api.storage.extension;

import ac.voidac.api.storage.model.BlobRef;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;

@ApiStatus.Experimental
public class NoSuchBlobException extends IOException {

    public NoSuchBlobException(BlobRef ref) {
        super("blob not found: " + ref);
    }
}
