import { MongoClient, type Db } from "mongodb";
import { ProviderError } from "./errors";

const cache = globalThis as unknown as { __nigixMongo?: Promise<MongoClient> };

export async function getDb(): Promise<Db> {
  const uri = process.env.MONGODB_URI?.trim();
  if (!uri) throw new ProviderError("Library storage is not configured (MONGODB_URI).", 503);
  if (!cache.__nigixMongo) {
    const client = new MongoClient(uri, { maxPoolSize: 10, serverSelectionTimeoutMS: 10_000 });
    cache.__nigixMongo = client
      .connect()
      .then(async (c) => {
        const db = c.db();
        await Promise.all([
          db.collection("projects").createIndex({ ownerId: 1, updatedAt: -1 }),
          db.collection("users").createIndex({ username: 1 }, { unique: true }),
          db.collection("sessions").createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 }),
          db.collection("sessions").createIndex({ userId: 1 }),
        ]);
        return c;
      })
      .catch((err: Error) => {
        cache.__nigixMongo = undefined;
        throw new ProviderError(`Can't reach the database: ${err.message}`, 503);
      });
  }
  return (await cache.__nigixMongo).db();
}
