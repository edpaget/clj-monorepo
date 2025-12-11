// Entry file for esbuild to bundle npm dependencies
// shadow-cljs will use this bundle via :js-provider :external

import * as React from "react";
import * as ReactDOM from "react-dom";
import { createRoot, hydrateRoot } from "react-dom/client";
import * as ApolloClient from "@apollo/client";
import * as ApolloClientTesting from "@apollo/client/testing";
import * as RadixAlertDialog from "@radix-ui/react-alert-dialog";
import * as RadixSelect from "@radix-ui/react-select";
import * as ReactRouterDom from "react-router-dom";
import * as LucideReact from "lucide-react";
import { cva } from "class-variance-authority";
import { clsx } from "clsx";
import { twMerge } from "tailwind-merge";

// Export for shadow-cljs external provider
window.React = React;
window.ReactDOM = ReactDOM;

const modules = {
  "react": React,
  "react-dom": ReactDOM,
  "react-dom/client": { createRoot, hydrateRoot },
  "@apollo/client": ApolloClient,
  "@apollo/client/testing": ApolloClientTesting,
  "@radix-ui/react-alert-dialog": RadixAlertDialog,
  "@radix-ui/react-select": RadixSelect,
  "react-router-dom": ReactRouterDom,
  "lucide-react": LucideReact,
  "class-variance-authority": { cva },
  "clsx": { clsx },
  "tailwind-merge": { twMerge },
};

globalThis.shadow$bridge = function(name) {
  const mod = modules[name];
  if (!mod) {
    throw new Error("Module not found in shadow$bridge: " + name);
  }
  return mod;
};
