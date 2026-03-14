// Небольшой скрипт, чтобы запускать анимацию после загрузки DOM.
// Так новичку проще понять, "когда" срабатывает эффект.

document.addEventListener("DOMContentLoaded", () => {
  const heroContainer = document.querySelector(".hero__container");

  if (!heroContainer) return;

  heroContainer.classList.add("is-visible");

  // Анимация градиента, следующего за курсором
  const cursorGradient = document.getElementById("cursorGradient");
  const hero = document.querySelector(".hero");

  if (cursorGradient && hero) {
    let mouseX = window.innerWidth / 2;
    let mouseY = window.innerHeight / 2;
    let currentX = mouseX;
    let currentY = mouseY;
    let isAnimating = false;

    // Функция плавного обновления позиции
    const updatePosition = () => {
      currentX += (mouseX - currentX) * 0.1;
      currentY += (mouseY - currentY) * 0.1;

      cursorGradient.style.left = currentX - 300 + "px";
      cursorGradient.style.top = currentY - 300 + "px";

      // Продолжаем анимацию, пока есть движение
      if (Math.abs(mouseX - currentX) > 0.5 || Math.abs(mouseY - currentY) > 0.5) {
        requestAnimationFrame(updatePosition);
      } else {
        isAnimating = false;
      }
    };

    // Обновляем позицию градиента при движении мыши
    hero.addEventListener("mousemove", (e) => {
      mouseX = e.clientX;
      mouseY = e.clientY;
      cursorGradient.classList.add("is-active");

      // Запускаем анимацию только если она еще не запущена
      if (!isAnimating) {
        isAnimating = true;
        updatePosition();
      }
    });

    // Скрываем градиент, когда мышь покидает область
    hero.addEventListener("mouseleave", () => {
      cursorGradient.classList.remove("is-active");
      isAnimating = false;
    });
  }

  // Логика работы с навыками
  const skillsGrid = document.getElementById("skills-grid");
  const addSkillBtn = document.getElementById("add-skill-btn");
  const STORAGE_KEY = "mySkills";

  // Загрузка навыков из localStorage
  function loadSkills() {
    const savedSkills = localStorage.getItem(STORAGE_KEY);
    if (savedSkills) {
      try {
        const skills = JSON.parse(savedSkills);
        skills.forEach((skill) => renderSkillCard(skill));
      } catch (e) {
        console.error("Ошибка при загрузке навыков:", e);
      }
    }
  }

  // Сохранение навыков в localStorage
  function saveSkills() {
    const skillCards = skillsGrid.querySelectorAll(".skill-card");
    const skills = Array.from(skillCards).map((card) => ({
      title: card.querySelector(".skill-card__title").textContent,
      description: card.querySelector(".skill-card__description").textContent,
      isLearned: card.classList.contains("is-learned"),
    }));
    localStorage.setItem(STORAGE_KEY, JSON.stringify(skills));
  }

  // Создание карточки навыка
  function renderSkillCard(skill) {
    const card = document.createElement("div");
    card.className = "skill-card";
    if (skill.isLearned) {
      card.classList.add("is-learned");
    }
    card.innerHTML = `
      <div class="skill-card__header">
        <h3 class="skill-card__title">${escapeHtml(skill.title)}</h3>
        <div class="skill-card__actions">
          <span class="skill-card__toggle">▼</span>
          <button class="skill-card__learned" type="button" aria-label="Отметить как изученный">✓</button>
          <button class="skill-card__delete" type="button" aria-label="Удалить навык">×</button>
        </div>
      </div>
      <p class="skill-card__description">${escapeHtml(skill.description)}</p>
    `;

    // Обработчик клика для разворачивания/сворачивания
    card.addEventListener("click", (e) => {
      // Не разворачиваем карточку, если клик был на кнопке
      if (
        e.target.classList.contains("skill-card__delete") ||
        e.target.classList.contains("skill-card__learned")
      ) {
        return;
      }
      card.classList.toggle("is-expanded");
      saveSkills();
    });

    // Обработчик кнопки "изучен"
    const learnedBtn = card.querySelector(".skill-card__learned");
    if (learnedBtn) {
      learnedBtn.addEventListener("click", (e) => {
        e.stopPropagation(); // Предотвращаем всплытие события
        toggleLearned(card);
      });
    }

    // Обработчик кнопки удаления
    const deleteBtn = card.querySelector(".skill-card__delete");
    if (deleteBtn) {
      deleteBtn.addEventListener("click", (e) => {
        e.stopPropagation(); // Предотвращаем всплытие события
        deleteSkill(card);
      });
    }

    skillsGrid.appendChild(card);
  }

  // Переключение статуса "изучен"
  function toggleLearned(card) {
    card.classList.toggle("is-learned");
    saveSkills();
  }

  // Удаление навыка
  function deleteSkill(card) {
    const title = card.querySelector(".skill-card__title").textContent;
    const confirmed = confirm(`Вы уверены, что хотите удалить навык "${title}"?`);
    
    if (confirmed) {
      card.remove();
      saveSkills();
    }
  }

  // Экранирование HTML для безопасности
  function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
  }

  // Добавление нового навыка
  function addSkill() {
    const title = prompt("Введите название навыка:");
    if (!title || title.trim() === "") {
      return;
    }

    const description = prompt("Введите описание навыка:");
    if (!description || description.trim() === "") {
      return;
    }

    const skill = {
      title: title.trim(),
      description: description.trim(),
    };

    renderSkillCard(skill);
    saveSkills();
  }

  // Обработчик кнопки добавления
  if (addSkillBtn) {
    addSkillBtn.addEventListener("click", addSkill);
  }

  // Загружаем навыки при загрузке страницы
  if (skillsGrid) {
    loadSkills();
  }

  // Логика работы с индексом навыков
  const SKILL_INDEX_STORAGE_KEY = "skillIndexValues";
  const skillRanges = document.querySelectorAll(".skill-index__range");
  const skillOutputs = document.querySelectorAll(".skill-index__output");
  const gaugeFill = document.getElementById("gauge-fill");
  const gaugeValue = document.getElementById("gauge-value");
  const skillDescription = document.getElementById("skill-description");

  // Загрузка значений из localStorage
  function loadSkillIndex() {
    const savedValues = localStorage.getItem(SKILL_INDEX_STORAGE_KEY);
    if (savedValues) {
      try {
        const values = JSON.parse(savedValues);
        skillRanges.forEach((range) => {
          const skillKey = range.dataset.skill;
          if (values[skillKey] !== undefined) {
            range.value = values[skillKey];
            updateOutput(range);
          }
        });
        updateAverage();
      } catch (e) {
        console.error("Ошибка при загрузке индекса навыков:", e);
      }
    }
  }

  // Сохранение значений в localStorage
  function saveSkillIndex() {
    const values = {};
    skillRanges.forEach((range) => {
      values[range.dataset.skill] = parseInt(range.value, 10);
    });
    localStorage.setItem(SKILL_INDEX_STORAGE_KEY, JSON.stringify(values));
  }

  // Обновление output для конкретного слайдера
  function updateOutput(range) {
    const output = document.querySelector(`output[for="${range.id}"]`);
    if (output) {
      output.textContent = `${range.value}%`;
    }
  }

  // Расчет среднего значения и обновление гейджа
  function updateAverage() {
    let sum = 0;
    skillRanges.forEach((range) => {
      sum += parseInt(range.value, 10);
    });
    const average = Math.round(sum / skillRanges.length);
    
    // Обновление гейджа
    if (gaugeFill) {
      gaugeFill.style.width = `${average}%`;
    }
    
    // Обновление значения
    if (gaugeValue) {
      gaugeValue.textContent = `${average}%`;
    }
    
    // Обновление описания
    if (skillDescription) {
      skillDescription.textContent = getSkillLevelDescription(average);
    }
  }

  // Определение уровня по диапазону
  function getSkillLevelDescription(percentage) {
    if (percentage >= 0 && percentage <= 25) {
      return "Начинающий, верный старт";
    } else if (percentage >= 26 && percentage <= 50) {
      return "Прогрессируешь, держи темп";
    } else if (percentage >= 51 && percentage <= 75) {
      return "Уверенно растешь, почти junior";
    } else if (percentage >= 76 && percentage <= 100) {
      return "Сильная база - готов к портфолио";
    }
    return "Начинающий, верный старт";
  }

  // Обработчики событий для слайдеров
  skillRanges.forEach((range) => {
    // Обновление при изменении значения
    range.addEventListener("input", () => {
      updateOutput(range);
      updateAverage();
      saveSkillIndex();
    });

    // Инициализация output при загрузке
    updateOutput(range);
  });

  // Инициализация при загрузке страницы
  if (skillRanges.length > 0) {
    loadSkillIndex();
    updateAverage();
  }
});

