import { register } from '../core/registry.js';
import { RectElement } from './RectElement.js';
import { EllipseElement } from './EllipseElement.js';
import { TextElement } from './TextElement.js';
import { ImageElement } from './ImageElement.js';

register('rect', RectElement);
register('ellipse', EllipseElement);
register('text', TextElement);
register('image', ImageElement);

export { RectElement, EllipseElement, TextElement, ImageElement };
