import { createContext } from "react";
import type { ApiError } from "../../api/errors";
import type { CartView } from "../../api/types";

/** What a failed cart change was for, so the screen can show the error next to the right line. */
export interface CartFailure {
  productId: string;
  error: unknown;
}

export interface CartState {
  /** The cart as the server last returned it; absent until the first response. */
  cart: CartView | undefined;
  isLoading: boolean;
  /** The first load failed; there is nothing to show yet. */
  loadError: ApiError | Error | null;
  reload: () => void;
  /** A change is in flight. */
  busy: boolean;
  /** The last change that the server refused or that could not be sent; cleared by the next change. */
  failure: CartFailure | undefined;
  add: (productId: string) => Promise<void>;
  setQuantity: (productId: string, quantity: number) => Promise<void>;
  remove: (productId: string) => Promise<void>;
}

export const CartContext = createContext<CartState | null>(null);
