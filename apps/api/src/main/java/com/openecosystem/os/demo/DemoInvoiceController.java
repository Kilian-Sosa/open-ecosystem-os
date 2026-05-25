package com.openecosystem.os.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/invoice-automation")
public class DemoInvoiceController {

  private final DemoInvoiceService demoInvoiceService;

  public DemoInvoiceController(DemoInvoiceService demoInvoiceService) {
    this.demoInvoiceService = demoInvoiceService;
  }

  @PostMapping("/runs")
  public DemoInvoiceRunResponse startRun() {
    return demoInvoiceService.startRun();
  }

  @GetMapping("/runs/{runId}")
  public DemoInvoiceRunResponse getRun(@PathVariable String runId) {
    return demoInvoiceService.getRun(runId);
  }

  @PostMapping("/reset")
  public DemoInvoiceResetResponse reset() {
    return demoInvoiceService.reset();
  }
}
