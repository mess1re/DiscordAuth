import discord
from discord.ext import commands, tasks
import aiohttp
import aiohttp.web as web
import logging
import os
from dotenv import load_dotenv
from database import (
    init_db, clean_expired_codes, load_codes_from_json,
    get_code, delete_code, get_code_by_discord_id
)
from commands import setup_commands
import json
from datetime import datetime
import time

# --- Rate-limit middleware для API ---
from aiohttp.web import middleware

api_rate_limits = {}

@middleware
async def api_rate_limit_middleware(request, handler):
    ip = request.remote
    now = time.time()
    rl = api_rate_limits.get(ip, 0)
    if now - rl < 1:  # 1 запрос в сек
        return web.json_response({'error': 'Too many requests'}, status=429)
    api_rate_limits[ip] = now
    return await handler(request)

# --- Logging ---
logging.basicConfig(
    filename='bot.log',
    level=logging.INFO,
    format='%(asctime)s:%(levelname)s:%(message)s'
)

# --- Загрузка .env ---
load_dotenv()
TOKEN = os.getenv('DISCORD_TOKEN')
GUILD_ID = int(os.getenv('GUILD_ID'))
ROLE_ID = int(os.getenv('ROLE_ID'))
API_URL = os.getenv('API_URL')
COMMAND_CHANNEL_ID = int(os.getenv('COMMAND_CHANNEL_ID'))

intents = discord.Intents.default()
intents.message_content = True
intents.members = True

bot = commands.Bot(command_prefix='!', intents=intents)

# --- API endpoints ---
async def has_role(request):
    discord_id = request.query.get('discordId')
    if not discord_id:
        logging.warning("API /hasRole: missing discordId")
        return web.json_response({"hasRole": False, "error": "Missing discordId"}, status=400)

    guild = bot.get_guild(GUILD_ID)
    if not guild:
        logging.error("API /hasRole: guild not found")
        return web.json_response({"hasRole": False, "error": "Guild not found"}, status=500)

    try:
        member = guild.get_member(int(discord_id))
        if not member:
            logging.warning(f"API /hasRole: member {discord_id} not found")
            return web.json_response({"hasRole": False, "error": "Member not found"})
    except ValueError:
        logging.warning(f"API /hasRole: invalid discordId {discord_id}")
        return web.json_response({"hasRole": False, "error": "Invalid discordId"}, status=400)

    has_role = any(role.id == ROLE_ID for role in member.roles)
    return web.json_response({"hasRole": has_role})

async def verify_code(request):
    try:
        data = await request.json()
        code = data.get('code')
        mc_nick = data.get('mcNick')
    except json.JSONDecodeError:
        logging.warning("API /verifyCode: invalid JSON")
        return web.json_response({"error": "Invalid JSON"}, status=400)

    if not code or not mc_nick:
        logging.warning(f"API /verifyCode: missing code or mcNick (code={code}, mcNick={mc_nick})")
        return web.json_response({"error": "Missing code or mcNick"}, status=400)

    code_data = await get_code(code)
    if not code_data:
        logging.warning(f"API /verifyCode: code {code} not found")
        return web.json_response({"error": "Code not found"}, status=400)

    if code_data['mc_nick'] != mc_nick:
        logging.warning(f"API /verifyCode: mcNick mismatch (expected {code_data['mc_nick']}, got {mc_nick})")
        return web.json_response({"error": "Invalid mcNick"}, status=400)

    if code_data['expires'] < datetime.utcnow():
        await delete_code(code)
        logging.warning(f"API /verifyCode: code {code} expired")
        return web.json_response({"error": "Code expired"}, status=400)

    guild = bot.get_guild(GUILD_ID)
    try:
        member = guild.get_member(int(code_data['discord_id']))
        if not member:
            logging.warning(f"API /verifyCode: member {code_data['discord_id']} not found")
            return web.json_response({"error": "Member not found"}, status=400)
    except ValueError:
        logging.warning(f"API /verifyCode: invalid discordId {code_data['discord_id']}")
        return web.json_response({"error": "Invalid discordId"}, status=400)

    has_role = any(role.id == ROLE_ID for role in member.roles)
    if not has_role:
        return web.json_response({"hasRole": False, "error": "Role not found"})

    return web.json_response({"hasRole": True, "discordId": code_data['discord_id']})

async def unlink(request):
    try:
        data = await request.json()
        discord_id = data.get('discordId')
    except json.JSONDecodeError:
        logging.warning("API /unlink: invalid JSON")
        return web.json_response({"error": "Invalid JSON"}, status=400)

    if not discord_id:
        logging.warning("API /unlink: missing discordId")
        return web.json_response({"error": "Missing discordId"}, status=400)

    code_data = await get_code_by_discord_id(discord_id)
    if code_data:
        await delete_code(code_data["code"])
        logging.info(f"API /unlink: removed code for Discord ID {discord_id}")
    return web.json_response({"success": True})

# --- Periodic cleanup ---
@tasks.loop(minutes=1)
async def clean_codes_task():
    await clean_expired_codes()
    logging.info("Expired codes cleaned")

@bot.event
async def on_ready():
    logging.info(f'Бот {bot.user} подключен к Discord!')
    guild = bot.get_guild(GUILD_ID)
    if guild:
        logging.info(f'Подключен к серверу: {guild.name} (ID: {guild.id})')
    else:
        logging.error(f"Сервер с ID {GUILD_ID} не найден!")
    clean_codes_task.start()

@bot.event
async def on_command_error(ctx, error):
    if isinstance(error, commands.MissingRequiredArgument):
        await ctx.send("Укажите все необходимые аргументы! Пример: `!link <код>` или `!generate <ник>`", delete_after=5)
    elif isinstance(error, commands.CommandNotFound):
        await ctx.send("Неизвестная команда!", delete_after=5)
    elif isinstance(error, commands.MissingPermissions):
        await ctx.send("У вас нет прав для этой команды!", delete_after=5)
    else:
        logging.error(f"Ошибка команды: {error}")
        await ctx.send("Произошла ошибка при выполнении команды!", delete_after=5)

async def start_api():
    app = web.Application(middlewares=[api_rate_limit_middleware])
    app.router.add_get('/api/hasRole', has_role)
    app.router.add_post('/api/verifyCode', verify_code)
    app.router.add_post('/api/unlink', unlink)
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, '0.0.0.0', 5000)
    await site.start()
    logging.info("API сервер запущен на http://0.0.0.0:5000")

async def shutdown(signal):
    logging.info(f"Shutting down with signal {signal}...")
    await bot.close()

async def main():
    await init_db()
    await load_codes_from_json()
    setup_commands(bot, GUILD_ID, ROLE_ID, API_URL, COMMAND_CHANNEL_ID)
    await start_api()
    try:
        await bot.start(TOKEN)
    finally:
        logging.info("Bot stopped.")

if __name__ == '__main__':
    import asyncio
    import signal

    loop = asyncio.get_event_loop()

    for s in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(
            s, lambda s=s: asyncio.create_task(shutdown(s.name))
        )

    try:
        loop.run_until_complete(main())
    except Exception as e:
        logging.error(f"Ошибка при запуске: {e}")