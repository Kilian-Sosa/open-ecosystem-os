import type { Metadata } from "next";
import type { ReactNode } from "react";

import { ThemeProvider, ThemeScript } from "@/components/theme";

import "./globals.css";

export const metadata: Metadata = {
  title: "Open Ecosystem OS",
  description: "Self-hosted productivity ecosystem workspace.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <ThemeScript />
      </head>
      <body>
        <ThemeProvider>{children}</ThemeProvider>
      </body>
    </html>
  );
}
