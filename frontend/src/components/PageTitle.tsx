/**
 * Título da aba por rota (issue #114), no formato "<Tela> · GoalFather".
 *
 * O React 19 trata `<title>` (assim como `<meta>` e `<link>`) como metadado
 * de documento: renderizado em qualquer ponto da árvore, ele é "içado" para o
 * `<head>`. Por isso não há `useEffect` mexendo em `document.title` — o título
 * vive e morre com o componente, sem estado sincronizado à mão nem cleanup.
 *
 * `children` é `string` (e não `ReactNode`) de propósito: o React exige um
 * único texto dentro de `<title>`, então montamos o template aqui.
 */
export function PageTitle({ children }: { children: string }) {
  return <title>{`${children} · GoalFather`}</title>
}
