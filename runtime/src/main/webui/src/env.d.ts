declare module "*.csv?raw" {
  const content: string;
  export default content;
}

interface ImportMetaEnv {
  readonly MODE: string;
  readonly VITE_DEMO_MODE?: string;
  readonly VITE_TRIAL_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
