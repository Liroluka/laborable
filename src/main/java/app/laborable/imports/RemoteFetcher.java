package app.laborable.imports;

import java.net.URI;

/** Downloads a text document from an official source. */
public interface RemoteFetcher {

  String fetch(URI uri);
}
