import { useRef, useState } from "react";

/**
 * Área de envio de CSV.
 * state: { status: "idle" | "uploading" | "done" | "error", fileName?, message? }
 */
export default function Dropzone({ title, description, state, onFile }) {
  const inputRef = useRef(null);
  const [dragging, setDragging] = useState(false);
  const busy = state.status === "uploading";

  function handleFiles(files) {
    const file = files?.[0];
    if (file && !busy) onFile(file);
  }

  return (
    <div>
      <h3 className="text-sm font-semibold">{title}</h3>
      <p className="mb-2 text-xs text-muted">{description}</p>

      <button
        type="button"
        disabled={busy}
        onClick={() => inputRef.current?.click()}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          handleFiles(e.dataTransfer.files);
        }}
        className={`flex min-h-28 w-full cursor-pointer flex-col items-center justify-center rounded-md border-2 border-dashed px-4 py-5 text-center transition-colors disabled:cursor-wait ${
          dragging ? "border-accent bg-accent/5" : "border-rule bg-surface hover:border-accent"
        }`}
      >
        {state.status === "uploading" && <span className="text-sm text-muted">Enviando {state.fileName}…</span>}
        {state.status === "done" && (
          <>
            <span className="text-sm font-medium text-ok">{state.fileName}</span>
            <span className="mt-1 text-xs text-muted">{state.message} Clique para trocar o arquivo.</span>
          </>
        )}
        {state.status === "error" && (
          <>
            <span className="text-sm font-medium text-bad">Falha ao enviar {state.fileName}</span>
            <span className="mt-1 text-xs text-muted">{state.message}</span>
          </>
        )}
        {state.status === "idle" && (
          <>
            <span className="text-sm font-medium">Arraste o CSV aqui</span>
            <span className="mt-1 text-xs text-muted">ou clique para escolher um arquivo</span>
          </>
        )}
      </button>

      <input
        ref={inputRef}
        type="file"
        accept=".csv,text/csv"
        className="sr-only"
        tabIndex={-1}
        onChange={(e) => {
          handleFiles(e.target.files);
          e.target.value = "";
        }}
      />
    </div>
  );
}
