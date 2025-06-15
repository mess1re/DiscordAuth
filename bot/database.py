import aiosqlite
from datetime import datetime
import json
import os
import logging
import time

DB_PATH = 'codes.db'
JSON_PATH = 'codes.json'

logging.basicConfig(
    filename='bot.log',
    level=logging.INFO,
    format='%(asctime)s:%(levelname)s:%(message)s'
)

# Rate-limit: discord_id: timestamp
rate_limit_cache = {}

async def init_db():
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute('''
            CREATE TABLE IF NOT EXISTS codes (
                code TEXT PRIMARY KEY,
                mc_nick TEXT NOT NULL UNIQUE,
                discord_id TEXT NOT NULL UNIQUE,
                expires TEXT NOT NULL
            )
        ''')
        await db.commit()
    logging.info("Database initialized")

async def save_code(code, mc_nick, discord_id, expires):
    """Сохранение кода в базу, заменяет существующий для discord_id и mc_nick"""
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute('DELETE FROM codes WHERE discord_id = ? OR mc_nick = ?', (discord_id, mc_nick))
        await db.execute('''
            INSERT INTO codes (code, mc_nick, discord_id, expires)
            VALUES (?, ?, ?, ?)
        ''', (code, mc_nick, discord_id, expires.isoformat()))
        await db.commit()
    await save_codes_to_json()
    logging.info(f"Code {code} saved for {mc_nick} (Discord ID: {discord_id})")

async def get_code(code):
    """Получение кода из базы"""
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute('SELECT mc_nick, discord_id, expires FROM codes WHERE code = ?', (code,)) as cursor:
            row = await cursor.fetchone()
            if row:
                return {
                    "mc_nick": row[0],
                    "discord_id": row[1],
                    "expires": datetime.fromisoformat(row[2])
                }
    logging.warning(f"Code {code} not found")
    return None

async def get_code_by_discord_id(discord_id):
    """Поиск кода по Discord ID (возвращает dict или None)"""
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute('SELECT code, mc_nick, expires FROM codes WHERE discord_id = ?', (discord_id,)) as cursor:
            row = await cursor.fetchone()
            if row:
                return {
                    "code": row[0],
                    "mc_nick": row[1],
                    "expires": datetime.fromisoformat(row[2])
                }
    return None

async def get_code_by_mc_nick(mc_nick):
    """Поиск кода по Minecraft нику (dict или None)"""
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute('SELECT code, discord_id, expires FROM codes WHERE mc_nick = ?', (mc_nick,)) as cursor:
            row = await cursor.fetchone()
            if row:
                return {
                    "code": row[0],
                    "discord_id": row[1],
                    "expires": datetime.fromisoformat(row[2])
                }
    return None

async def delete_code(code):
    """Удаление кода из базы"""
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute('DELETE FROM codes WHERE code = ?', (code,))
        await db.commit()
    await save_codes_to_json()
    logging.info(f"Code {code} deleted")

async def clean_expired_codes():
    """Очистка просроченных кодов"""
    now = datetime.utcnow().isoformat()
    async with aiosqlite.connect(DB_PATH) as db:
        await db.execute('DELETE FROM codes WHERE expires < ?', (now,))
        await db.commit()
    await save_codes_to_json()
    # Чистим rate_limit_cache (старше 1 минуты)
    global rate_limit_cache
    now_t = time.time()
    rate_limit_cache = {k: v for k, v in rate_limit_cache.items() if now_t - v < 60}
    logging.info("Expired codes cleaned")

async def save_codes_to_json():
    """Резервное сохранение кодов в JSON"""
    async with aiosqlite.connect(DB_PATH) as db:
        async with db.execute('SELECT code, mc_nick, discord_id, expires FROM codes') as cursor:
            codes = await cursor.fetchall()
        data = {
            code: {
                "mc_nick": mc_nick,
                "discord_id": discord_id,
                "expires": expires
            } for code, mc_nick, discord_id, expires in codes
        }
        with open(JSON_PATH, 'w') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    logging.info("Codes saved to JSON")

async def load_codes_from_json():
    """Загрузка кодов из JSON"""
    if os.path.exists(JSON_PATH):
        with open(JSON_PATH) as f:
            data = json.load(f)
        async with aiosqlite.connect(DB_PATH) as db:
            for code, info in data.items():
                await db.execute('''
                    INSERT OR IGNORE INTO codes (code, mc_nick, discord_id, expires)
                    VALUES (?, ?, ?, ?)
                ''', (code, info['mc_nick'], info['discord_id'], info['expires']))
            await db.commit()
        logging.info("Codes loaded from JSON")

def can_generate_code(discord_id, cooldown=60):
    now = time.time()
    last = rate_limit_cache.get(discord_id, 0)
    if now - last < cooldown:
        return False
    rate_limit_cache[discord_id] = now
    return True