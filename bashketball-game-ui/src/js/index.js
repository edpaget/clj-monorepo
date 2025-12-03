// Entry file for esbuild to bundle npm dependencies
// shadow-cljs will use this bundle via :js-provider :external

import * as React from "react";
import * as ReactDOM from "react-dom";
import { createRoot, hydrateRoot } from "react-dom/client";
import * as ApolloClient from "@apollo/client";
import * as ApolloClientReact from "@apollo/client/react";
import * as ApolloClientTesting from "@apollo/client/testing";
import * as ApolloClientTestingReact from "@apollo/client/testing/react";
import { print as gqlPrint } from "graphql";
import * as RadixDialog from "@radix-ui/react-dialog";
import * as RadixDropdownMenu from "@radix-ui/react-dropdown-menu";
import * as RadixLabel from "@radix-ui/react-label";
import * as RadixSelect from "@radix-ui/react-select";
import * as RadixSlot from "@radix-ui/react-slot";
import * as RadixTabs from "@radix-ui/react-tabs";
import * as RadixTooltip from "@radix-ui/react-tooltip";
import * as ReactRouterDom from "react-router-dom";
import * as LucideReact from "lucide-react";
import { cva } from "class-variance-authority";
import { clsx } from "clsx";
import { twMerge } from "tailwind-merge";
import { Observable } from "rxjs";

// Export for shadow-cljs external provider
window.React = React;
window.ReactDOM = ReactDOM;

const modules = {
  "react": React,
  "react-dom": ReactDOM,
  "react-dom/client": { createRoot, hydrateRoot },
  "@apollo/client": { ...ApolloClient, ...ApolloClientReact },
  "@apollo/client/react": ApolloClientReact,
  "@apollo/client/testing": { ...ApolloClientTesting, ...ApolloClientTestingReact },
  "@apollo/client/testing/react": ApolloClientTestingReact,
  "graphql": { print: gqlPrint },
  "@radix-ui/react-dialog": RadixDialog,
  "@radix-ui/react-dropdown-menu": RadixDropdownMenu,
  "@radix-ui/react-label": RadixLabel,
  "@radix-ui/react-select": RadixSelect,
  "@radix-ui/react-slot": RadixSlot,
  "@radix-ui/react-tabs": RadixTabs,
  "@radix-ui/react-tooltip": RadixTooltip,
  "react-router-dom": ReactRouterDom,
  "lucide-react": LucideReact,
  "class-variance-authority": { cva },
  "clsx": { clsx },
  "tailwind-merge": { twMerge },
  "rxjs": { Observable },
};

globalThis.shadow$bridge = function(name) {
  const mod = modules[name];
  if (!mod) {
    throw new Error("Module not found in shadow$bridge: " + name);
  }
  return mod;
};
