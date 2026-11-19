// Shared Tailwind class strings, so buttons look the same on every page.
const base = 'rounded-lg px-3 py-2 text-sm font-medium disabled:opacity-60'

export const button = {
  primary: `${base} bg-blue-600 text-white hover:bg-blue-700`,
  secondary: `${base} border border-slate-300 bg-white text-slate-700 hover:bg-slate-100`,
  danger: `${base} border border-red-200 bg-white text-red-700 hover:bg-red-50`,
}

export const input =
  'w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-200 aria-invalid:border-red-500'

export const card = 'rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200'
