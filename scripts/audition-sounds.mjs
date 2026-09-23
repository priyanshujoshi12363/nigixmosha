import fs from "node:fs";
import http from "node:http";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const here = path.join(root, "scripts", "sounds");
const picksFile = path.join(here, "picks.json");
const port = Number(process.env.PORT ?? 5180);

const candidates = JSON.parse(fs.readFileSync(path.join(here, "candidates.json"), "utf8"));
const template = fs.readFileSync(path.join(here, "audition.html"), "utf8");

const readPicks = () => (fs.existsSync(picksFile) ? JSON.parse(fs.readFileSync(picksFile, "utf8")) : {});

function page() {
  return template
    .replace("__DATA__", JSON.stringify(candidates))
    .replace("__PICKS__", JSON.stringify(readPicks()));
}

const server = http.createServer(async (req, res) => {
  if (req.method === "POST" && req.url === "/api/pick") {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    try {
      const { tag, pick } = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      const picks = readPicks();
      if (pick === null) delete picks[tag];
      else picks[tag] = pick;
      fs.writeFileSync(picksFile, JSON.stringify(picks, null, 2) + "\n");
      const kept = Object.values(picks).filter((p) => !p.skipped).length;
      process.stdout.write(`\r${kept} kept, ${Object.keys(picks).length} of ${candidates.length} decided   `);
      res.writeHead(200, { "content-type": "application/json" });
      res.end('{"ok":true}');
    } catch (err) {
      res.writeHead(400, { "content-type": "application/json" });
      res.end(JSON.stringify({ error: String(err) }));
    }
    return;
  }
  res.writeHead(200, { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" });
  res.end(page());
});

server.listen(port, "0.0.0.0", () => {
  const lan = Object.values(os.networkInterfaces())
    .flat()
    .find((n) => n && n.family === "IPv4" && !n.internal);
  console.log(`audition  http://localhost:${port}`);
  if (lan) console.log(`on phone  http://${lan.address}:${port}`);
  console.log(`picks     ${path.relative(root, picksFile)}\n`);
});
