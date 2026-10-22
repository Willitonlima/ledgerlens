import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import Dropzone from "./Dropzone.jsx";

describe("Dropzone", () => {
  it("opens an accessible file input and forwards a selected CSV", () => {
    const onFile = vi.fn();
    render(
      <Dropzone
        description="CSV exportado do ERP"
        onFile={onFile}
        state={{ status: "idle" }}
        title="Arquivo do ERP"
      />,
    );

    const file = new File(["data,descricao,valor"], "erp.csv", { type: "text/csv" });
    fireEvent.change(screen.getByLabelText("Arquivo do ERP"), {
      target: { files: [file] },
    });

    expect(onFile).toHaveBeenCalledWith(file);
  });

  it("shows the uploaded filename and record count", () => {
    render(
      <Dropzone
        description="CSV do ERP"
        onFile={vi.fn()}
        state={{ status: "done", fileName: "erp.csv", recordsImported: 12 }}
        title="Arquivo do ERP"
      />,
    );

    expect(screen.getAllByText("erp.csv")).toHaveLength(2);
    expect(screen.getByText(/12 lançamentos importados/)).toBeInTheDocument();
  });

  it("renders validation details by row after an upload error", () => {
    render(
      <Dropzone
        description="CSV do banco"
        onFile={vi.fn()}
        state={{
          status: "error",
          fileName: "banco.csv",
          message: "Confira os campos do arquivo.",
          errors: [{ line: 3, field: "valor", message: "O valor é obrigatório." }],
        }}
        title="Extrato bancário"
      />,
    );

    expect(screen.getByText("Linha 3, valor: O valor é obrigatório.")).toBeInTheDocument();
  });
});
