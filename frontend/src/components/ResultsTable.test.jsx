import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import ResultsTable from "./ResultsTable.jsx";

const items = [{
  id: "match-1",
  date: "2025-01-02",
  description: "Compra de materiais",
  amount: 125.5,
  status: "MATCHED",
  origin: "BOTH",
  differenceAmount: 0,
  notes: "Correspondência por referência.",
}];

describe("ResultsTable", () => {
  it("renders the complete results table", () => {
    render(<ResultsTable items={items} loading={false} />);
    const table = screen.getByRole("table");

    for (const heading of ["Data", "Descrição", "Valor", "Origem", "Status", "Diferença", "Observação"]) {
      expect(within(table).getByRole("columnheader", { name: heading })).toBeInTheDocument();
    }
    expect(within(table).getByText("Compra de materiais")).toBeInTheDocument();
  });

  it("includes an accessible card layout for mobile screens", () => {
    render(<ResultsTable items={items} loading={false} />);
    const cards = screen.getByRole("list", { name: "Resultados da conciliação" });
    expect(within(cards).getByText("Compra de materiais")).toBeInTheDocument();
    for (const label of ["Valor", "Origem", "Diferença", "Observação"]) {
      expect(within(cards).getByText(label)).toBeInTheDocument();
    }
  });

  it("announces loading and guides an empty state", () => {
    const { rerender } = render(<ResultsTable items={[]} loading />);
    expect(screen.getByLabelText("Carregando resultados")).toBeInTheDocument();

    rerender(<ResultsTable items={[]} loading={false} />);
    expect(screen.getByText(/Envie os arquivos do ERP e do banco/)).toBeInTheDocument();
  });
});
