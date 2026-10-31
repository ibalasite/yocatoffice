// features/support/world.ts
// Auto-generated World fixture for yocat-office-backend Server BDD (Cucumber.js).
// 介面依 BDD-server.gen.md §A World Fixture Factory；fixture key = SCHEMA.md BC 名稱。
// 註：runtime lang_stack 為 Java 21 + Spring Boot 3.3；此 TypeScript harness 用於 BDD 驗收層
//     （Cucumber.js + axios 直打 HTTP 契約），與 Spring 服務以 API_BASE_URL 對接。

import { IWorldOptions, World, setWorldConstructor } from '@cucumber/cucumber';
import axios, { AxiosInstance } from 'axios';
import { createClient, RedisClientType } from 'redis';

// FixtureMap key = SCHEMA.md §BC ownership 的 BC/schema 名稱（不寫死）
export interface FixtureMap {
  [bcName: string]: Record<string, unknown>[];
}

export interface DbHelper {
  // 依 SCHEMA.md FK 順序 INSERT 測試資料（parent BC 先插）
  seed(map: FixtureMap): Promise<void>;
  // TRUNCATE 所有 business tables（排除 audit/log tables）
  clean(): Promise<void>;
  // 供 Then step 直接查 DB 驗證業務結果
  query<T = Record<string, unknown>>(sql: string, params?: unknown[]): Promise<{ rows: T[] }>;
}

export interface RedisHelper {
  // key pattern 嚴格引用 SCHEMA.md §19 Redis Key Schema（session: / rate_limit: / lb: 命名空間）
  set(key: string, value: string, ttl?: number): Promise<void>;
  get(key: string): Promise<string | null>;
  del(key: string): Promise<void>;
}

export interface AuthHelper {
  // role 名稱從 SCHEMA.md / EDD §3.3 讀取（USER / OPS，不寫死）
  loginAs(role: string, overrides?: Record<string, unknown>): Promise<string>; // 回傳 opaque session token
}

export interface ApiWorld {
  client: AxiosInstance;
  // 通用屬性
  lastResponse: { status: number; body: unknown };
  authToken: string | null;
  // 依 SCHEMA.md 各 BC 主鍵動態保存，供後續 step 串接
  accountId: string | null;
  orderId: string | null;
  idempotencyKey: string | null;
  runId: string | null;
  db: DbHelper;
  redis: RedisHelper;
  auth: AuthHelper;
}

class CustomWorld extends World implements ApiWorld {
  client: AxiosInstance;
  lastResponse: { status: number; body: unknown } = { status: 0, body: null };
  authToken: string | null = null;
  accountId: string | null = null;
  orderId: string | null = null;
  idempotencyKey: string | null = null;
  runId: string | null = null;
  db: DbHelper;
  redis: RedisHelper;
  auth: AuthHelper;

  private redisClient: RedisClientType;

  constructor(options: IWorldOptions) {
    super(options);
    this.client = axios.create({
      baseURL: process.env['API_BASE_URL'] ?? 'http://localhost:8080',
      validateStatus: () => true, // 不因 4xx/5xx throw，交由 Then step 斷言
    });

    // Redis 測試專用 DB index（與生產不同，SCHEMA.md §19 / EDD §11.3）
    this.redisClient = createClient({
      url: process.env['REDIS_TEST_URL'] ?? 'redis://localhost:6379/15',
    });
    this.redis = {
      set: (key, value, ttl) =>
        (ttl
          ? this.redisClient.setEx(key, ttl, value)
          : this.redisClient.set(key, value)
        ).then(() => undefined),
      get: (key) => this.redisClient.get(key),
      del: (key) => this.redisClient.del(key).then(() => undefined),
    };

    this.db = {
      // TODO: 以 pg Pool 依 SCHEMA.md FK 順序 INSERT（member → wallet → entitlement → ...）
      seed: async (_map: FixtureMap) => {
        return; // TODO: implement fixture seeding（見 SCHEMA.md §3 表定義）
      },
      // TODO: TRUNCATE member.accounts, wallet.wallets, wallet.wallet_ledger, entitlement.entitlements,
      //       record.records, leaderboard.leaderboard, game_save.saves, deposit.deposit_order（RESTART IDENTITY CASCADE）
      clean: async () => {
        return; // TODO: implement truncate of business tables（排除 audit/log）
      },
      // TODO: 以 pg 參數化查詢回傳 rows，供 Then 驗證持久化
      query: async <T = Record<string, unknown>>(_sql: string, _params?: unknown[]) => {
        return { rows: [] as T[] }; // TODO: implement
      },
    };

    this.auth = {
      // TODO: 直接 seed sessions 表簽發 token（避免依賴 login API），見 SCHEMA.md member.sessions
      loginAs: async (_role: string, _overrides?: Record<string, unknown>) => {
        return 'test-session-token-0000'; // TODO: implement — seed sessions 並回傳 token
      },
    };
  }
}

setWorldConstructor(CustomWorld);
