import { useId, useRef, useState } from "react";

export default function Dropzone({ title, description, state, onFile }) {
  const inputRef = useRef(null);
  const inputId = useId();
  const descriptionId = useId();
  const [dragging, setDragging] = useState(false);
  const busy = state.status === "uploading";

  function handleFiles(files) {
    const file = files?.[0];
    if (file && !busy) onFile(file);
  }

  return (
    <div className="upload-card">
      <label className="upload-title" htmlFor={inputId}>{title}</label>
      <p className="upload-description" id={descriptionId}>{description}</p>
      <button
        aria-describedby={descriptionId}
        aria-label={`${title}: escolher arquivo CSV`}
        aria-busy={busy}
        className={`dropzone${dragging ? " is-dragging" : ""}`}
        disabled={busy}
        onClick={() => inputRef.current?.click()}
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault();
          setDragging(false);
          handleFiles(event.dataTransfer.files);
        }}
        type="button"
      >
        {busy && <span className="dropzone-state">Enviando {state.fileName}…</span>}
        {state.status === "done" && (
          <>
            <span className="dropzone-state success">{state.fileName}</span>
            <span className="dropzone-hint">{state.recordsImported} lançamentos importados. Clique para trocar.</span>
          </>
        )}
        {state.status === "error" && (
          <>
            <span className="dropzone-state error">Não foi possível enviar {state.fileName}</span>
            <span className="dropzone-hint">{state.message}</span>
          </>
        )}
        {state.status === "idle" && (
          <>
            <span className="dropzone-state">Arraste o CSV até aqui</span>
            <span className="dropzone-hint">ou escolha um arquivo no seu dispositivo</span>
          </>
        )}
      </button>
      <input
        accept=".csv,text/csv"
        className="visually-hidden"
        id={inputId}
        onChange={(event) => {
          handleFiles(event.target.files);
          event.target.value = "";
        }}
        ref={inputRef}
        tabIndex={-1}
        type="file"
      />
      {state.status === "error" && state.errors?.length > 0 && (
        <ul className="upload-errors" aria-label="Erros encontrados no arquivo">
          {state.errors.map((item, index) => (
            <li key={`${item.line}-${item.field}-${index}`}>
              Linha {item.line}, {item.field}: {item.message}
            </li>
          ))}
        </ul>
      )}
      {state.status === "done" && state.fileName && (
        <p className="uploaded-file" aria-live="polite">
          Arquivo enviado: <strong>{state.fileName}</strong> · {state.recordsImported} lançamentos
        </p>
      )}
    </div>
  );
}
