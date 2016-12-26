package com.vlkan.hrrs.api.base64;

import com.vlkan.hrrs.api.HttpRequestRecord;
import com.vlkan.hrrs.api.HttpRequestRecordSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.vlkan.hrrs.api.base64.Base64HttpRequestRecord.RECORD_SEPARATOR;

public class Base64HttpRequestRecordReaderIterator implements Iterator<HttpRequestRecord> {

    private final HttpRequestRecordSource source;

    private final Base64Decoder decoder;

    private long lineIndex = -1;

    private String line;

    Base64HttpRequestRecordReaderIterator(HttpRequestRecordSource source, Base64Decoder decoder) {
        this.source = checkNotNull(source, "source");
        this.decoder = checkNotNull(decoder, "decoder");
    }

    @Override
    public boolean hasNext() {
        BufferedReader reader = source.getReader();
        try {
            return reader.ready() && tryReadingLine(reader);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    private boolean tryReadingLine(BufferedReader reader) throws IOException {
        line = reader.readLine();
        if (line != null) {
            lineIndex++;
            return true;
        }
        return false;
    }

    @Override
    public HttpRequestRecord next() {
        checkArgument(lineIndex >= 0, "hasNext() should have been called first");
        try {
            int separatorIndex = line.indexOf(RECORD_SEPARATOR);
            if (separatorIndex < 0) {
                String message = String.format("could not locate the field separator (lineIndex=%d)", lineIndex);
                throw new RuntimeException(message);
            }
            String encodedRecordBytes = line.substring(separatorIndex + RECORD_SEPARATOR.length());
            byte[] recordBytes = decoder.decode(encodedRecordBytes);
            return HttpRequestRecord.parseFrom(recordBytes);
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

}
