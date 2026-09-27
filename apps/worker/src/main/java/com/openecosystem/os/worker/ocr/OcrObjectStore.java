package com.openecosystem.os.worker.ocr;

import java.io.IOException;
import java.io.InputStream;

public interface OcrObjectStore {

  InputStream open(String storageKey) throws IOException;
}
