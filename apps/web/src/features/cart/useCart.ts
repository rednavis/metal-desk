import { useContext } from "react";
import { CartContext, type CartState } from "./CartContext";

/** The customer's cart and the changes that can be made to it. Must be used under {@link CartProvider}. */
export function useCart(): CartState {
  const state = useContext(CartContext);
  if (state === null) throw new Error("useCart must be used inside a CartProvider");
  return state;
}
