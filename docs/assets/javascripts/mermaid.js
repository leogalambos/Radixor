const renderRadixorDiagrams = () => {
  mermaid.initialize({
    startOnLoad: false,
    theme: "neutral",
    flowchart: { htmlLabels: true, useMaxWidth: true },
  });
  return mermaid.run({ querySelector: ".mermaid" });
};

if (typeof document$ === "undefined") {
  document.addEventListener("DOMContentLoaded", renderRadixorDiagrams);
} else {
  document$.subscribe(renderRadixorDiagrams);
}
