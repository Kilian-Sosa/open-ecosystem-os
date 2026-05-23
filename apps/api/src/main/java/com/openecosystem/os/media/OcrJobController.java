package com.openecosystem.os.media;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media/ocr-jobs")
public class OcrJobController {

  private final OcrJobQueryService ocrJobQueryService;

  public OcrJobController(OcrJobQueryService ocrJobQueryService) {
    this.ocrJobQueryService = ocrJobQueryService;
  }

  @GetMapping
  public OcrJobListResponse listJobs() {
    return ocrJobQueryService.listJobs();
  }

  @GetMapping("/{jobId}")
  public OcrJobDetailResponse getJob(@PathVariable String jobId) {
    return ocrJobQueryService.getJob(jobId);
  }
}
