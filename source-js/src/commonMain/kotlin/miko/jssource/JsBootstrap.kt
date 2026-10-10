package miko.jssource

/**
 * JS sandbox bootstrap — module registry, FormData/URLSearchParams/Headers/
 * fetch-Response shims, Intl fallback. Ported verbatim from the Android
 * NovelJsRuntime bootstrapScript (engine-agnostic JS).
 */
object JsBootstrap {
        val script = listOf(
            NovelJsPromiseShim.script,
            """
                (function(global) {
                  var __modules = {};
                  var __moduleFactories = {};
                  global.__defineModule = function(name, factory) {
                    __moduleFactories[name] = factory;
                  };
                  global.require = function(name) {
                    if (__modules[name]) return __modules[name].exports;
                    var factory = __moduleFactories[name];
                    if (!factory) throw new Error("Module not found: " + name);
                    var module = { exports: {} };
                    __modules[name] = module;
                    factory(module, module.exports);
                    return module.exports;
                  };
                  function FormData() { this.__formDataEntries = []; }
                  FormData.prototype.append = function(key, value) {
                    this.__formDataEntries.push({ key: String(key), value: String(value) });
                  };
                  global.FormData = FormData;
                  function URLSearchParams(init) {
                    this._entries = [];
                    if (typeof init === "string") {
                      var pairs = init.split("&");
                      for (var i = 0; i < pairs.length; i++) {
                        if (!pairs[i]) continue;
                        var parts = pairs[i].split("=");
                        var key = decodeURIComponent(parts[0] || "");
                        var value = decodeURIComponent(parts.slice(1).join("=") || "");
                        this._entries.push([key, value]);
                      }
                    } else if (init && typeof init === "object") {
                      for (var key in init) {
                        if (Object.prototype.hasOwnProperty.call(init, key)) {
                          this._entries.push([String(key), String(init[key])]);
                        }
                      }
                    }
                  }
                  URLSearchParams.prototype.append = function(key, value) {
                    this._entries.push([String(key), String(value)]);
                  };
                  URLSearchParams.prototype.set = function(key, value) {
                    var k = String(key);
                    var v = String(value);
                    var replaced = false;
                    var filtered = [];
                    for (var i = 0; i < this._entries.length; i++) {
                      var entry = this._entries[i];
                      if (entry[0] === k) {
                        if (!replaced) {
                          filtered.push([k, v]);
                          replaced = true;
                        }
                      } else {
                        filtered.push(entry);
                      }
                    }
                    if (!replaced) filtered.push([k, v]);
                    this._entries = filtered;
                  };
                  URLSearchParams.prototype.get = function(key) {
                    var k = String(key);
                    for (var i = 0; i < this._entries.length; i++) {
                      if (this._entries[i][0] === k) return this._entries[i][1];
                    }
                    return null;
                  };
                  URLSearchParams.prototype.has = function(key) {
                    var k = String(key);
                    for (var i = 0; i < this._entries.length; i++) {
                      if (this._entries[i][0] === k) return true;
                    }
                    return false;
                  };
                  URLSearchParams.prototype.delete = function(key) {
                    var k = String(key);
                    var filtered = [];
                    for (var i = 0; i < this._entries.length; i++) {
                      if (this._entries[i][0] !== k) filtered.push(this._entries[i]);
                    }
                    this._entries = filtered;
                  };
                  URLSearchParams.prototype.toString = function() {
                    return this._entries
                      .map(function(entry) {
                        return encodeURIComponent(entry[0]) + "=" + encodeURIComponent(entry[1]);
                      })
                      .join("&");
                  };
                  global.URLSearchParams = URLSearchParams;

                  function Headers(init) {
                    this._headers = {};
                    if (init) {
                      if (typeof Headers !== "undefined" && init instanceof Headers) {
                        init.forEach(function(value, key) {
                          this._headers[key.toLowerCase()] = String(value);
                        }, this);
                      } else if (Array.isArray(init)) {
                        for (var i = 0; i < init.length; i++) {
                          this.append(init[i][0], init[i][1]);
                        }
                      } else if (init && typeof init === "object") {
                        for (var key in init) {
                          if (Object.prototype.hasOwnProperty.call(init, key)) {
                            this.append(key, init[key]);
                          }
                        }
                      }
                    }
                  }
                  Headers.prototype.get = function(name) {
                    var key = String(name).toLowerCase();
                    return Object.prototype.hasOwnProperty.call(this._headers, key) ? this._headers[key] : null;
                  };
                  Headers.prototype.set = function(name, value) {
                    this._headers[String(name).toLowerCase()] = String(value);
                  };
                  Headers.prototype.append = function(name, value) {
                    var key = String(name).toLowerCase();
                    if (Object.prototype.hasOwnProperty.call(this._headers, key)) {
                      this._headers[key] += ", " + String(value);
                    } else {
                      this._headers[key] = String(value);
                    }
                  };
                  Headers.prototype.has = function(name) {
                    return Object.prototype.hasOwnProperty.call(this._headers, String(name).toLowerCase());
                  };
                  Headers.prototype.delete = function(name) {
                    delete this._headers[String(name).toLowerCase()];
                  };
                  Headers.prototype.forEach = function(callback, thisArg) {
                    for (var key in this._headers) {
                      if (Object.prototype.hasOwnProperty.call(this._headers, key)) {
                        callback.call(thisArg, this._headers[key], key, this);
                      }
                    }
                  };
                  Headers.prototype.keys = function() {
                    var result = [];
                    for (var key in this._headers) {
                      if (Object.prototype.hasOwnProperty.call(this._headers, key)) result.push(key);
                    }
                    return result;
                  };
                  Headers.prototype.toJSON = function() {
                    var result = {};
                    for (var key in this._headers) {
                      if (Object.prototype.hasOwnProperty.call(this._headers, key)) {
                        result[key] = this._headers[key];
                      }
                    }
                    return result;
                  };
                  global.Headers = Headers;

                  function URL(input, base) {
                    var resolved = __native.resolveUrl(String(input), base != null ? String(base) : null);
                    this.pathname = __native.getPathname(resolved);
                    // Split URL into base + query + fragment so toString() can
                    // rebuild including mutations to this.searchParams.
                    var qi = resolved.indexOf('?');
                    var hi = resolved.indexOf('#');
                    if (qi >= 0) {
                      this._baseUrl = resolved.substring(0, qi);
                      var qEnd = hi >= 0 ? hi : resolved.length;
                      this._fragment = hi >= 0 ? resolved.substring(hi) : '';
                      this.searchParams = new URLSearchParams(resolved.substring(qi + 1, qEnd));
                    } else if (hi >= 0) {
                      this._baseUrl = resolved.substring(0, hi);
                      this._fragment = resolved.substring(hi);
                      this.searchParams = new URLSearchParams('');
                    } else {
                      this._baseUrl = resolved;
                      this._fragment = '';
                      this.searchParams = new URLSearchParams('');
                    }
                  }
                  URL.prototype.toString = function() {
                    var qs = this.searchParams.toString();
                    return this._baseUrl + (qs ? '?' + qs : '') + this._fragment;
                  };
                  URL.prototype.valueOf = function() {
                    return this.toString();
                  };
                  URL.prototype.toJSON = function() {
                    return this.toString();
                  };
                  Object.defineProperty(URL.prototype, 'href', {
                    get: function() { return this.toString(); },
                    set: function(v) { this._baseUrl = v; },
                    configurable: true,
                    enumerable: true
                  });
                  global.URL = URL;

                  global.console = {
                    log: function() {
                      var args = [];
                      for (var i = 0; i < arguments.length; i++) {
                        try { args.push(String(arguments[i])); } catch(e) { args.push('[object]'); }
                      }
                      __native.consoleLog(args.join(' '));
                    },
                    error: function() {
                      var args = [];
                      for (var i = 0; i < arguments.length; i++) {
                        try { args.push(String(arguments[i])); } catch(e) { args.push('[object]'); }
                      }
                      __native.consoleError(args.join(' '));
                    },
                    warn: function() {
                      var args = [];
                      for (var i = 0; i < arguments.length; i++) {
                        try { args.push(String(arguments[i])); } catch(e) { args.push('[object]'); }
                      }
                      __native.consoleWarn(args.join(' '));
                    },
                    info: function() {
                      var args = [];
                      for (var i = 0; i < arguments.length; i++) {
                        try { args.push(String(arguments[i])); } catch(e) { args.push('[object]'); }
                      }
                      __native.consoleLog(args.join(' '));
                    },
                    debug: function() {}
                  };

                  if (!Array.prototype.flat) {
                    Array.prototype.flat = function(depth) {
                      depth = depth === undefined ? 1 : Math.floor(Number(depth));
                      if (depth < 1) return this.slice();
                      return this.reduce(function(acc, val) {
                        if (Array.isArray(val) && depth > 0) {
                          acc.push.apply(acc, val.flat(depth - 1));
                        } else {
                          acc.push(val);
                        }
                        return acc;
                      }, []);
                    };
                  }
                  if (!Array.prototype.flatMap) {
                    Array.prototype.flatMap = function(fn, thisArg) {
                      return this.map(fn, thisArg).flat(1);
                    };
                  }
                  if (!Array.prototype.includes) {
                    Array.prototype.includes = function(value, fromIndex) {
                      var start = fromIndex || 0;
                      for (var i = start; i < this.length; i++) {
                        if (this[i] === value || (this[i] !== this[i] && value !== value)) return true;
                      }
                      return false;
                    };
                  }
                  if (!Array.prototype.find) {
                    Array.prototype.find = function(fn, thisArg) {
                      for (var i = 0; i < this.length; i++) {
                        if (fn.call(thisArg, this[i], i, this)) return this[i];
                      }
                      return undefined;
                    };
                  }
                  if (!Array.prototype.findIndex) {
                    Array.prototype.findIndex = function(fn, thisArg) {
                      for (var i = 0; i < this.length; i++) {
                        if (fn.call(thisArg, this[i], i, this)) return i;
                      }
                      return -1;
                    };
                  }
                  if (!Array.from) {
                    Array.from = function(iterable, mapFn, thisArg) {
                      var arr = [];
                      if (iterable.length !== undefined) {
                        for (var i = 0; i < iterable.length; i++) {
                          arr.push(mapFn ? mapFn.call(thisArg, iterable[i], i) : iterable[i]);
                        }
                      }
                      return arr;
                    };
                  }
                  if (!Object.entries) {
                    Object.entries = function(obj) {
                      var entries = [];
                      for (var key in obj) {
                        if (Object.prototype.hasOwnProperty.call(obj, key)) {
                          entries.push([key, obj[key]]);
                        }
                      }
                      return entries;
                    };
                  }
                  if (!Object.values) {
                    Object.values = function(obj) {
                      var values = [];
                      for (var key in obj) {
                        if (Object.prototype.hasOwnProperty.call(obj, key)) {
                          values.push(obj[key]);
                        }
                      }
                      return values;
                    };
                  }
                  if (!Object.assign) {
                    Object.assign = function(target) {
                      for (var i = 1; i < arguments.length; i++) {
                        var source = arguments[i];
                        if (source != null) {
                          for (var key in source) {
                            if (Object.prototype.hasOwnProperty.call(source, key)) {
                              target[key] = source[key];
                            }
                          }
                        }
                      }
                      return target;
                    };
                  }
                  if (!String.prototype.includes) {
                    String.prototype.includes = function(search, start) {
                      return this.indexOf(search, start || 0) !== -1;
                    };
                  }
                  if (!String.prototype.startsWith) {
                    String.prototype.startsWith = function(search, pos) {
                      pos = pos || 0;
                      return this.substr(pos, search.length) === search;
                    };
                  }
                  if (!String.prototype.endsWith) {
                    String.prototype.endsWith = function(search, thisLen) {
                      if (thisLen === undefined) thisLen = this.length;
                      return this.substring(thisLen - search.length, thisLen) === search;
                    };
                  }
                  if (!String.prototype.trimStart) {
                    String.prototype.trimStart = function() { return this.replace(/^\s+/, ''); };
                  }
                  if (!String.prototype.trimEnd) {
                    String.prototype.trimEnd = function() { return this.replace(/\s+$/, ''); };
                  }
                  if (!String.prototype.padStart) {
                    String.prototype.padStart = function(len, fill) {
                      var s = String(this);
                      fill = fill || ' ';
                      while (s.length < len) s = fill + s;
                      return s.substring(s.length - len);
                    };
                  }
                  if (!String.prototype.padEnd) {
                    String.prototype.padEnd = function(len, fill) {
                      var s = String(this);
                      fill = fill || ' ';
                      while (s.length < len) s = s + fill;
                      return s.substring(0, len);
                    };
                  }
                  if (!String.prototype.replaceAll) {
                    String.prototype.replaceAll = function(search, replacement) {
                      return this.split(search).join(replacement);
                    };
                  }
                  if (!Number.isFinite) {
                    Number.isFinite = function(value) {
                      return typeof value === 'number' && isFinite(value);
                    };
                  }
                  if (!Number.isNaN) {
                    Number.isNaN = function(value) {
                      return typeof value === 'number' && value !== value;
                    };
                  }
                  if (typeof global.Intl === 'undefined') {
                    global.Intl = {};
                    global.Intl.DateTimeFormat = function(locale, options) {
                      return {
                        format: function(date) {
                          try { date = new Date(date); return date.toISOString().split('T')[0]; }
                          catch(e) { return String(date); }
                        },
                        resolvedOptions: function() { return {}; }
                      };
                    };
                    global.Intl.NumberFormat = function(locale, options) {
                      return {
                        format: function(n) { return String(n); },
                        resolvedOptions: function() { return {}; }
                      };
                    };
                    global.Intl.Collator = function(locale, options) {
                      return {
                        compare: function(a, b) {
                          a = String(a || ''); b = String(b || '');
                          return a < b ? -1 : a > b ? 1 : 0;
                        },
                        resolvedOptions: function() { return {}; }
                      };
                    };
                  }
                })(this);
            """.trimIndent(),
            """
                (function(global) {
                  // Web globals J2V8 ships but JavaScriptCore lacks.
                  if (typeof global.btoa !== "function") {
                    var B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
                    global.btoa = function(input) {
                      var str = String(input);
                      var out = "";
                      for (var i = 0; i < str.length; i += 3) {
                        var c1 = str.charCodeAt(i);
                        var c2 = i + 1 < str.length ? str.charCodeAt(i + 1) : NaN;
                        var c3 = i + 2 < str.length ? str.charCodeAt(i + 2) : NaN;
                        var n = (c1 << 16) | ((isNaN(c2) ? 0 : c2) << 8) | (isNaN(c3) ? 0 : c3);
                        out += B64.charAt((n >> 18) & 63) + B64.charAt((n >> 12) & 63) +
                               (isNaN(c2) ? "=" : B64.charAt((n >> 6) & 63)) +
                               (isNaN(c3) ? "=" : B64.charAt(n & 63));
                      }
                      return out;
                    };
                  }
                  if (typeof global.atob !== "function") {
                    var B64C = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
                    global.atob = function(input) {
                      var str = String(input).replace(/[^A-Za-z0-9+\/]/g, "");
                      var out = "";
                      for (var i = 0; i < str.length; i += 4) {
                        var c1 = B64C.indexOf(str.charAt(i));
                        var c2 = B64C.indexOf(str.charAt(i + 1));
                        var c3 = str.charAt(i + 2) === "" ? -1 : B64C.indexOf(str.charAt(i + 2));
                        var c4 = str.charAt(i + 3) === "" ? -1 : B64C.indexOf(str.charAt(i + 3));
                        if (c1 < 0 || c2 < 0) break;
                        var n = (c1 << 18) | (c2 << 12) | ((c3 < 0 ? 0 : c3) << 6) | (c4 < 0 ? 0 : c4);
                        out += String.fromCharCode((n >> 16) & 255);
                        if (c3 >= 0) out += String.fromCharCode((n >> 8) & 255);
                        if (c4 >= 0) out += String.fromCharCode(n & 255);
                      }
                      return out;
                    };
                  }
                })(this);
            """.trimIndent(),
        ).joinToString("\n")
}
