/**
 * Junta classes CSS ignorando valores falsy (`false`, `undefined`, `''`), para
 * compor variantes sem template strings cheias de ternários.
 *
 * Não resolve conflitos entre utilitários (não é `tailwind-merge`): quem usa um
 * componente de `ui/` deve passar em `className` só classes que não disputem a
 * mesma propriedade com as da variante (ex.: `w-full`, `mt-1`, `whitespace-nowrap`).
 */
export function cx(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(' ')
}
