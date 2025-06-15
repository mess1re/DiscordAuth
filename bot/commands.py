from discord.ext import commands
import discord
import aiohttp
import logging
from datetime import datetime, timedelta
import random
import string
from database import save_code, get_code, delete_code, get_code_by_discord_id, get_code_by_mc_nick, can_generate_code

def generate_code(length=8):
    return ''.join(random.choices(string.ascii_letters + string.digits, k=length))

def has_required_role(member, role_id):
    return any(role.id == role_id for role in member.roles)

def setup_commands(bot, guild_id, role_id, api_url, command_channel_id):
    @bot.command(name='generate')
    async def generate_code_cmd(ctx, mc_nick: str):
        if ctx.channel.id != command_channel_id:
            await ctx.send("Эта команда доступна только в специальном канале!", delete_after=5)
            return

        if not has_required_role(ctx.author, role_id):
            await ctx.send("У вас нет нужной роли для генерации кода!", delete_after=5)
            return

        if not can_generate_code(str(ctx.author.id)):
            await ctx.send("Слишком часто! Подождите минуту перед следующей попыткой.", delete_after=5)
            return

        # Проверка уникальности Discord ID и ника
        if await get_code_by_discord_id(str(ctx.author.id)):
            await ctx.send("У вас уже есть активный код или привязка! Используйте !mylinks или !unlink.", delete_after=5)
            return

        if await get_code_by_mc_nick(mc_nick):
            await ctx.send("Этот ник уже привязан к другому Discord-аккаунту!", delete_after=5)
            return

        code = generate_code()
        expires = datetime.utcnow() + timedelta(minutes=5)
        await save_code(code, mc_nick, str(ctx.author.id), expires)

        try:
            await ctx.author.send(f"Ваш код для привязки аккаунта **{mc_nick}**: `{code}`\n"
                                  f"Используйте в игре: `/discordlink {code}`\n"
                                  f"Код действителен 5 минут!")
            await ctx.send("Код отправлен в личные сообщения!", delete_after=5)
        except discord.Forbidden:
            await ctx.send("Не удалось отправить ЛС! Включите личные сообщения от сервера.", delete_after=5)
            logging.warning(f"Failed to send DM to {ctx.author.id}")

    @bot.command(name='link')
    async def link_account(ctx, code: str):
        if ctx.channel.id != command_channel_id:
            await ctx.send("Эта команда доступна только в специальном канале!", delete_after=5)
            return

        if not has_required_role(ctx.author, role_id):
            await ctx.send("У вас нет нужной роли для авторизации!", delete_after=5)
            return

        code_data = await get_code(code)
        if not code_data:
            await ctx.send("Недействительный код!", delete_after=5)
            return

        if code_data['expires'] < datetime.utcnow():
            await delete_code(code)
            await ctx.send("Код истёк!", delete_after=5)
            return

        if code_data['discord_id'] != str(ctx.author.id):
            await ctx.send("Этот код принадлежит другому пользователю!", delete_after=5)
            return

        mc_nick = code_data['mc_nick']
        discord_id = str(ctx.author.id)

        async with aiohttp.ClientSession() as session:
            payload = {"code": code, "mcNick": mc_nick}
            try:
                async with session.post(f"{api_url}/api/verifyCode", json=payload) as resp:
                    if resp.status != 200:
                        await ctx.send("Ошибка связи с сервером Minecraft!", delete_after=5)
                        logging.error(f"API /verifyCode failed: status {resp.status}")
                        return
                    data = await resp.json()
                    if data.get('hasRole', False) and data.get('discordId') == discord_id:
                        await delete_code(code)
                        await ctx.send(f"Аккаунт {mc_nick} успешно привязан!")
                    else:
                        await ctx.send("Ошибка: проверьте наличие роли или повторите позже!", delete_after=5)
                        logging.warning(f"API /verifyCode returned: {data}")
            except aiohttp.ClientError as e:
                logging.error(f"API /verifyCode error: {e}")
                await ctx.send("Ошибка связи с сервером!", delete_after=5)

    @bot.command(name='unlink')
    async def unlink_account(ctx):
        if ctx.channel.id != command_channel_id:
            await ctx.send("Эта команда доступна только в специальном канале!", delete_after=5)
            return

        discord_id = str(ctx.author.id)
        code_data = await get_code_by_discord_id(discord_id)
        if not code_data:
            await ctx.send("У вас нет активной привязки.", delete_after=5)
            return

        await delete_code(code_data["code"])

        async with aiohttp.ClientSession() as session:
            payload = {"discordId": discord_id}
            try:
                async with session.post(f"{api_url}/api/unlink", json=payload) as resp:
                    if resp.status != 200:
                        logging.error(f"API /unlink failed: status {resp.status}")
                    else:
                        logging.info(f"API /unlink called for Discord ID {discord_id}")
            except aiohttp.ClientError as e:
                logging.error(f"API /unlink error: {e}")

        await ctx.send("Ваша привязка удалена.", delete_after=5)
        try:
            await ctx.author.send("Ваша привязка была удалена!")
        except discord.Forbidden:
            logging.warning(f"Failed to send DM to {ctx.author.id}")

    @bot.command(name='admin_unlink')
    @commands.has_permissions(administrator=True)
    async def admin_unlink(ctx, user: discord.User):
        discord_id = str(user.id)
        code_data = await get_code_by_discord_id(discord_id)
        if code_data:
            await delete_code(code_data["code"])
            async with aiohttp.ClientSession() as session:
                payload = {"discordId": discord_id}
                try:
                    async with session.post(f"{api_url}/api/unlink", json=payload) as resp:
                        if resp.status != 200:
                            logging.error(f"API /unlink failed: status {resp.status}")
                        else:
                            logging.info(f"API /unlink called for Discord ID {discord_id}")
                except aiohttp.ClientError as e:
                    logging.error(f"API /unlink error: {e}")

        await ctx.send(f"Привязка для {user.mention} удалена.", delete_after=5)
        try:
            await user.send("Ваша привязка была удалена администратором.")
        except discord.Forbidden:
            logging.warning(f"Failed to send DM to {user.id}")

    @bot.command(name='listlinks')
    @commands.has_permissions(administrator=True)
    async def list_links_admin(ctx, user: discord.User):
        code_data = await get_code_by_discord_id(str(user.id))
        if not code_data:
            await ctx.send(f"У {user.mention} нет активной привязки.", delete_after=5)
            return
        line = f"Код `{code_data['code']}` для ника **{code_data['mc_nick']}** (до {code_data['expires']})"
        await ctx.send(f"Привязка для {user.mention}:\n" + line, delete_after=30)

    @bot.command(name='mylinks')
    async def my_links(ctx):
        if ctx.channel.id != command_channel_id:
            await ctx.send("Эта команда доступна только в специальном канале!", delete_after=5)
            return

        code_data = await get_code_by_discord_id(str(ctx.author.id))
        if not code_data:
            await ctx.send("У вас нет активной привязки.", delete_after=5)
            return
        line = f"Код `{code_data['code']}` для ника **{code_data['mc_nick']}** (до {code_data['expires']})"
        try:
            await ctx.author.send("Ваша привязка:\n" + line)
            await ctx.send("Информация отправлена в личные сообщения!", delete_after=5)
        except discord.Forbidden:
            await ctx.send("Не удалось отправить ЛС! Включите личные сообщения от сервера.", delete_after=5)
            logging.warning(f"Failed to send DM to {ctx.author.id}")