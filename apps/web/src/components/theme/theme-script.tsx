import { THEME_STORAGE_KEY, THEME_SYSTEM_QUERY } from "@/lib/theme";

const themeScript = `
(function () {
  try {
    var storedPreference = window.localStorage.getItem("${THEME_STORAGE_KEY}");
    var preference = storedPreference === "light" || storedPreference === "dark" || storedPreference === "system"
      ? storedPreference
      : "system";
    var resolvedTheme = preference === "system"
      ? (window.matchMedia("${THEME_SYSTEM_QUERY}").matches ? "dark" : "light")
      : preference;
    var root = document.documentElement;
    root.setAttribute("data-theme", resolvedTheme);
    root.style.colorScheme = resolvedTheme;
  } catch (error) {
    document.documentElement.setAttribute("data-theme", "light");
    document.documentElement.style.colorScheme = "light";
  }
})();
`;

export function ThemeScript() {
  return <script dangerouslySetInnerHTML={{ __html: themeScript }} />;
}
