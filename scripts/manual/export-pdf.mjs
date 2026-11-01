/** Print this platform manual using the same HTML distributed to readers. */
import { chromium } from 'playwright';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../../', import.meta.url));
const browser = await chromium.launch({
  ...(process.env.CHROME_EXECUTABLE ? { executablePath: process.env.CHROME_EXECUTABLE } : {}),
});
try {
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const base = path.join(root, 'docs/manual-usuario');
  await page.goto(pathToFileURL(path.join(base, 'index.html')).href);
  await page.emulateMedia({ media: 'print' });
  await page.evaluate(async () => {
    document.querySelectorAll('details').forEach(node => { node.open = true; });
    document.querySelectorAll('img').forEach(img => { img.loading = 'eager'; });
    await Promise.all(Array.from(document.querySelectorAll("figure img"), img => img.decode()));
    await document.fonts.ready;
  });
  if (errors.length) throw new Error(errors.join('\n'));
  await page.pdf({
    path: path.join(base, 'manual.pdf'), format: 'A4', printBackground: true, preferCSSPageSize: true,
    displayHeaderFooter: true, headerTemplate: '<span></span>',
    footerTemplate: `<div style="font:9px sans-serif;color:#666859;width:100%;margin:0 14mm;display:flex;justify-content:space-between"><span>Raíz · App móvil</span><span class="pageNumber"></span></div>`,
  });
  console.log(`Exported ${path.join(base, 'manual.pdf')}`);
  await page.close();
} finally {
  await browser.close();
}
