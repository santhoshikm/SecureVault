const UPPERCASE = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const UPPERCASE_NO_AMB = 'ABCDEFGHJKMNPQRSTUVWXYZ'; // No I, O
const LOWERCASE = 'abcdefghijklmnopqrstuvwxyz';
const LOWERCASE_NO_AMB = 'abcdefghjkmnpqrstuvwxyz'; // No l, i, o
const NUMBERS = '0123456789';
const NUMBERS_NO_AMB = '23456789'; // No 0, 1
const SYMBOLS = '!@#$%^&*()_+-=[]{}|;:,.<>?';
const SYMBOLS_SAFE = '!@#$%&*_+-=';

const WORD_LIST = [
  'apple', 'bridge', 'cloud', 'dragon', 'ember', 'forest', 'garden', 'harbor',
  'island', 'jungle', 'knight', 'lantern', 'meadow', 'nebula', 'ocean', 'palace',
  'quartz', 'river', 'silver', 'timber', 'umbrella', 'valley', 'winter', 'xenon',
  'yellow', 'zenith', 'anchor', 'beacon', 'castle', 'delta', 'eagle', 'falcon',
  'glacier', 'horizon', 'iris', 'jaguar', 'karma', 'lotus', 'marble', 'nimbus',
  'orbit', 'prism', 'quasar', 'rocket', 'sunset', 'tiger', 'ultra', 'violet',
  'wave', 'xray', 'yoga', 'zeal', 'amber', 'bronze', 'coral', 'dune', 'echo',
  'flame', 'grove', 'hollow', 'indigo', 'jade', 'kite', 'lemon', 'maple', 'neon'
];

export function generatePassword(length = 16, options = {}) {
  const {
    uppercase = true,
    lowercase = true,
    numbers = true,
    symbols = true,
    excludeAmbiguous = false,
    customSymbols = '',
    prefix = '',
    minUppercase = 0,
    minNumbers = 0,
    minSymbols = 0,
  } = options;

  const upperSet = excludeAmbiguous ? UPPERCASE_NO_AMB : UPPERCASE;
  const lowerSet = excludeAmbiguous ? LOWERCASE_NO_AMB : LOWERCASE;
  const digitSet = excludeAmbiguous ? NUMBERS_NO_AMB : NUMBERS;
  const symbolSet = customSymbols.trim()
    ? customSymbols
    : (excludeAmbiguous ? SYMBOLS_SAFE : SYMBOLS);

  let pool = '';
  if (lowercase) pool += lowerSet;
  if (uppercase) pool += upperSet;
  if (numbers) pool += digitSet;
  if (symbols) pool += symbolSet;

  if (pool.length === 0) pool = lowerSet + upperSet + digitSet;

  const resultChars = [];

  const pickRandom = (str) => {
    const arr = new Uint32Array(1);
    crypto.getRandomValues(arr);
    return str[arr[0] % str.length];
  };

  // Guarantee minimums
  if (uppercase) {
    for (let i = 0; i < minUppercase; i++) resultChars.push(pickRandom(upperSet));
  }
  if (numbers) {
    for (let i = 0; i < minNumbers; i++) resultChars.push(pickRandom(digitSet));
  }
  if (symbols) {
    for (let i = 0; i < minSymbols; i++) resultChars.push(pickRandom(symbolSet));
  }

  const needed = Math.max(0, length - prefix.length - resultChars.length);
  for (let i = 0; i < needed; i++) {
    resultChars.push(pickRandom(pool));
  }

  // Shuffle using Fisher-Yates with crypto random values
  for (let i = resultChars.length - 1; i > 0; i--) {
    const arr = new Uint32Array(1);
    crypto.getRandomValues(arr);
    const j = arr[0] % (i + 1);
    [resultChars[i], resultChars[j]] = [resultChars[j], resultChars[i]];
  }

  return prefix + resultChars.slice(0, Math.max(0, length - prefix.length)).join('');
}

export function generatePassphrase(words = 4, separator = '-', capitalize = true, addNumber = true) {
  const chosen = [];
  for (let i = 0; i < words; i++) {
    const arr = new Uint32Array(1);
    crypto.getRandomValues(arr);
    let word = WORD_LIST[arr[0] % WORD_LIST.length];
    if (capitalize) word = word.charAt(0).toUpperCase() + word.slice(1);
    chosen.push(word);
  }
  let phrase = chosen.join(separator);
  if (addNumber) {
    const numArr = new Uint32Array(1);
    crypto.getRandomValues(numArr);
    const num = (numArr[0] % 9000) + 1000;
    phrase += `${separator}${num}`;
  }
  return phrase;
}

export function getPasswordStrength(password = '') {
  const length = password.length;
  const hasUpper = /[A-Z]/.test(password);
  const hasLower = /[a-z]/.test(password);
  const hasDigit = /[0-9]/.test(password);
  const hasSymbol = /[^a-zA-Z0-9]/.test(password);
  const hasRepeats = /(.)\1{2,}/.test(password);
  
  // Check sequential (abc, 123)
  let isSequential = false;
  const lower = password.toLowerCase();
  for (let i = 0; i < lower.length - 2; i++) {
    if (
      lower.charCodeAt(i + 1) === lower.charCodeAt(i) + 1 &&
      lower.charCodeAt(i + 2) === lower.charCodeAt(i) + 2
    ) {
      isSequential = true;
      break;
    }
  }

  let score = 0;
  if (length >= 8) score += 20;
  if (length >= 12) score += 15;
  if (length >= 16) score += 10;
  if (hasUpper) score += 12;
  if (hasLower) score += 12;
  if (hasDigit) score += 12;
  if (hasSymbol) score += 15;
  if (hasRepeats) score = Math.max(0, score - 10);
  if (isSequential) score = Math.max(0, score - 10);
  score = Math.min(100, Math.max(0, score));

  let label = 'Weak';
  let color = '#ef4444';
  let percentage = Math.max(15, score);

  if (score < 35) {
    label = 'Weak';
    color = '#ef4444';
  } else if (score < 65) {
    label = 'Medium';
    color = '#f59e0b';
  } else if (score < 85) {
    label = 'Strong';
    color = '#10b981';
  } else {
    label = 'Very Strong';
    color = '#6366f1';
  }

  // Crack time estimation
  let charsetSize = 0;
  if (hasLower) charsetSize += 26;
  if (hasUpper) charsetSize += 26;
  if (hasDigit) charsetSize += 10;
  if (hasSymbol) charsetSize += 32;
  if (charsetSize === 0) charsetSize = 26;

  const combinations = Math.pow(charsetSize, length);
  const seconds = combinations / 1e10; // 10 billion guesses/sec
  let crackTime = '< 1 second';
  if (seconds >= 3.15e9) crackTime = 'centuries';
  else if (seconds >= 31536000) crackTime = `${Math.round(seconds / 31536000)} years`;
  else if (seconds >= 2592000) crackTime = `${Math.round(seconds / 2592000)} months`;
  else if (seconds >= 86400) crackTime = `${Math.round(seconds / 86400)} days`;
  else if (seconds >= 3600) crackTime = `${Math.round(seconds / 3600)} hours`;
  else if (seconds >= 60) crackTime = `${Math.round(seconds / 60)} minutes`;
  else if (seconds >= 1) crackTime = `${Math.round(seconds)} seconds`;

  // Suggestions
  const suggestions = [];
  if (length < 12) suggestions.push('Increase length to at least 12 characters.');
  if (!hasUpper) suggestions.push('Add uppercase letters (A–Z).');
  if (!hasLower) suggestions.push('Add lowercase letters (a–z).');
  if (!hasDigit) suggestions.push('Add numbers (0–9).');
  if (!hasSymbol) suggestions.push('Add special characters (!@#$% etc.).');
  if (hasRepeats) suggestions.push('Avoid repeating identical characters 3+ times in a row.');
  if (isSequential) suggestions.push('Avoid predictable sequences like "abc" or "123".');
  if (suggestions.length === 0) suggestions.push('Excellent password! Strong and unpredictable.');

  return {
    score,
    label,
    color,
    percentage,
    crackTime,
    hasUpper,
    hasLower,
    hasDigit,
    hasSymbol,
    hasRepeats,
    isSequential,
    length,
    suggestions,
  };
}
