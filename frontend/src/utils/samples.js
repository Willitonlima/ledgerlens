const samples = {
  ERP: "/samples/erp.csv",
  BANK: "/samples/banco.csv",
};

export function sampleUrl(sourceType) {
  return samples[sourceType];
}
